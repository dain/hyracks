/*
 * Copyright 2009-2010 by The Regents of the University of California
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * you may obtain a copy of the License from
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.uci.ics.hyracks.algebricks.rewriter.rules;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.apache.commons.lang3.mutable.Mutable;
import org.apache.commons.lang3.mutable.MutableObject;

import edu.uci.ics.hyracks.algebricks.common.exceptions.AlgebricksException;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.ILogicalExpression;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.ILogicalOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.IOptimizationContext;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.LogicalOperatorTag;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.LogicalVariable;
import edu.uci.ics.hyracks.algebricks.core.algebra.expressions.ConstantExpression;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.AbstractLogicalOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.AbstractOperatorWithNestedPlans;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.EmptyTupleSourceOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.InnerJoinOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.visitors.VariableUtilities;
import edu.uci.ics.hyracks.algebricks.core.rewriter.base.IAlgebraicRewriteRule;

/**
 * Rewrite rule for producing joins from unnests. 
 * This rule is limited to creating left-deep trees. 
 */
public class SimpleUnnestToProductRule implements IAlgebraicRewriteRule {

    @Override
    public boolean rewritePre(Mutable<ILogicalOperator> opRef, IOptimizationContext context) {
        return false;
    }

    @Override
    public boolean rewritePost(Mutable<ILogicalOperator> opRef, IOptimizationContext context)
            throws AlgebricksException {
        AbstractLogicalOperator op = (AbstractLogicalOperator) opRef.getValue();
        if (op.getOperatorTag() != LogicalOperatorTag.DATASOURCESCAN
                && op.getOperatorTag() != LogicalOperatorTag.UNNEST) {
            return false;
        }
        
        // We follow a simple strategy of pulling selects above the join we create
        // in order to eliminate possible dependencies between
        // the outer and inner input plans of the join.
        List<ILogicalOperator> topSelects = new ArrayList<ILogicalOperator>();
        
        // Keep track of the operators and used variables participating in the inner input plan.
        HashSet<LogicalVariable> innerUsedVars = new HashSet<LogicalVariable>();
        List<ILogicalOperator> innerOps = new ArrayList<ILogicalOperator>();
        HashSet<LogicalVariable> outerUsedVars = new HashSet<LogicalVariable>();
        List<ILogicalOperator> outerOps = new ArrayList<ILogicalOperator>();
        innerOps.add(op);
        VariableUtilities.getUsedVariables(op, innerUsedVars);
        
        Mutable<ILogicalOperator> opRef2 = op.getInputs().get(0);
        AbstractLogicalOperator op2 = (AbstractLogicalOperator) opRef2.getValue();
        AbstractLogicalOperator unnestOrJoin = findNextUnnestOrJoin(op2, innerUsedVars, outerUsedVars, innerOps, outerOps, topSelects);
        if (unnestOrJoin == null) {
            return false;
        }
        
        ILogicalOperator outerRoot = null;
        ILogicalOperator innerRoot = null;
        EmptyTupleSourceOperator ets = new EmptyTupleSourceOperator();
        // If we found a join, simply use it as the outer root.
        if (unnestOrJoin.getOperatorTag() != LogicalOperatorTag.INNERJOIN && 
                unnestOrJoin.getOperatorTag() != LogicalOperatorTag.LEFTOUTERJOIN) {
            // We've found a second unnest. First, sanity check that the unnest does not produce any vars that are used by the plan above (until the first unnest).
            List<LogicalVariable> producedVars = new ArrayList<LogicalVariable>();
            VariableUtilities.getProducedVariables(unnestOrJoin, producedVars);
            for (LogicalVariable producedVar : producedVars) {
                if (innerUsedVars.contains(producedVar)) {
                    return false;
                }
            }
            // Determine a partitioning of the plan below the second unnest such that the first and the second unnest are independent.
            // This means that for each operator below the second unnest we should determine whether it belongs to the inner plan partition or the outer plan partition.
            // Keep track of the operators and used variables participating in the outer input plan.
            outerOps.add(unnestOrJoin);
            VariableUtilities.getUsedVariables(unnestOrJoin, outerUsedVars);
            AbstractLogicalOperator unnestChild = (AbstractLogicalOperator) unnestOrJoin.getInputs().get(0).getValue();
            if (!findPlanPartition(unnestChild, innerUsedVars, outerUsedVars, innerOps, outerOps, topSelects)) {
                return false;
            }
        }
        innerRoot = buildOperatorChain(innerOps, ets, context);
        context.computeAndSetTypeEnvironmentForOperator(innerRoot);
        outerRoot = buildOperatorChain(outerOps, null, context);
        context.computeAndSetTypeEnvironmentForOperator(outerRoot);
        
        InnerJoinOperator product = new InnerJoinOperator(
                new MutableObject<ILogicalExpression>(ConstantExpression.TRUE));
        // Outer branch.
        product.getInputs().add(new MutableObject<ILogicalOperator>(outerRoot));
        // Inner branch.
        product.getInputs().add(new MutableObject<ILogicalOperator>(innerRoot));
        context.computeAndSetTypeEnvironmentForOperator(product);
        // Put the selects on top of the join.
        ILogicalOperator topOp = product;
        if (!topSelects.isEmpty()) {
            topOp = buildOperatorChain(topSelects, product, context);
        }
        // Plug the selects + product in the plan.
        opRef.setValue(topOp);
        context.computeAndSetTypeEnvironmentForOperator(topOp);
        return true;
    }

    private ILogicalOperator buildOperatorChain(List<ILogicalOperator> ops, ILogicalOperator bottomOp, IOptimizationContext context) throws AlgebricksException {
        ILogicalOperator root = ops.get(0);
        ILogicalOperator prevOp = root;
        for (int i = 1; i < ops.size(); i++) {
            ILogicalOperator inputOp = ops.get(i);
            prevOp.getInputs().clear();
            prevOp.getInputs().add(new MutableObject<ILogicalOperator>(inputOp));
            prevOp = inputOp;            
        }
        if (bottomOp != null) {
            context.computeAndSetTypeEnvironmentForOperator(bottomOp);
            prevOp.getInputs().clear();
            prevOp.getInputs().add(new MutableObject<ILogicalOperator>(bottomOp));
        }
        return root;
    }
    
    private AbstractLogicalOperator findNextUnnestOrJoin(AbstractLogicalOperator op, HashSet<LogicalVariable> innerUsedVars, HashSet<LogicalVariable> outerUsedVars,
            List<ILogicalOperator> innerOps, List<ILogicalOperator> outerOps, List<ILogicalOperator> topSelects) throws AlgebricksException {
        switch (op.getOperatorTag()) {
            case SUBPLAN: {
                // Bail on subplan.
                return null;
            }
            case INNERJOIN:
            case LEFTOUTERJOIN: {
                if (!innerUsedVars.isEmpty()) {
                    // Make sure that no variables that are live under this join are needed by the inner.
                    List<LogicalVariable> liveVars = new ArrayList<LogicalVariable>();
                    VariableUtilities.getLiveVariables(op, liveVars);
                    for (LogicalVariable liveVar : liveVars) {
                        if (innerUsedVars.contains(liveVar)) {
                            return null;
                        }
                    }
                }
                // If there is already an independent join, just return it so we can hook it up to the unnest.
                outerOps.add(op);
                return op;
            }
            case UNNEST:
            case DATASOURCESCAN: {
                return op;
            }
            case SELECT: {
                // Remember this select to pulling it above the join.
                if (innerUsedVars.isEmpty()) {
                    outerOps.add(op);
                } else {
                    topSelects.add(op);
                }                
                break;
            }
            case PROJECT: {
                // Throw away projects from the plan since we are pulling selects up.
                break;
            }
            case EMPTYTUPLESOURCE: 
            case NESTEDTUPLESOURCE: {
                return null;
            }
            default: {
                if (innerUsedVars.isEmpty()) {
                    outerOps.add(op);
                    break;
                }
                List<LogicalVariable> producedVars = new ArrayList<LogicalVariable>();
                VariableUtilities.getProducedVariables(op, producedVars);
                if (producedVars.isEmpty()) {
                    // TODO: We should not throw here, just for debugging.
                    //throw new AlgebricksException("No produced variables in operator of type '" + op.getOperatorTag() + "'.");
                }
                int outerMatches = 0;
                int innerMatches = 0;           
                for (LogicalVariable producedVar : producedVars) {
                    if (outerUsedVars.contains(producedVar)) {
                        outerMatches++;
                    } else if (innerUsedVars.contains(producedVar)) {
                        innerMatches++;
                    }
                }
                
                HashSet<LogicalVariable> targetUsedVars = null;
                if (outerMatches == producedVars.size() && !producedVars.isEmpty()) {
                    // Definitely part of the outer partition.
                    outerOps.add(op);
                    targetUsedVars = outerUsedVars;
                } else if (innerMatches == producedVars.size() && !producedVars.isEmpty()) {
                    // Definitely part of the inner partition.
                    innerOps.add(op);
                    targetUsedVars = innerUsedVars;
                } else if (innerMatches == 0 && outerMatches == 0){
                    // Op produces a variable that is not used in the part of the plan we've seen. Try to figure out where it belongs by analyzing the used variables.
                    List<LogicalVariable> usedVars = new ArrayList<LogicalVariable>();
                    VariableUtilities.getUsedVariables(op, usedVars);
                    for (LogicalVariable usedVar : usedVars) {
                        if (outerUsedVars.contains(usedVar)) {
                            outerOps.add(op);
                            targetUsedVars = outerUsedVars;
                            break;
                        }
                        if (innerUsedVars.contains(usedVar)) {
                            innerOps.add(op);
                            targetUsedVars = innerUsedVars;
                            break;
                        }
                    }
                    if (targetUsedVars == null) {
                        return null;
                    }
                } else {
                    // The current operator produces variables that are used by both partitions, so the inner and outer are not independent and, therefore, we cannot create a join.
                    // TODO: We may still be able to split the operator to create a viable partitioning.
                    return null;
                }
                // Update used variables of partition that op belongs to.
                if (op.hasNestedPlans() && op.getOperatorTag() != LogicalOperatorTag.SUBPLAN) {
                    AbstractOperatorWithNestedPlans opWithNestedPlans = (AbstractOperatorWithNestedPlans) op;
                    opWithNestedPlans.getUsedVariablesExceptNestedPlans(targetUsedVars);
                } else {
                    VariableUtilities.getUsedVariables(op, targetUsedVars);
                }
                break;
            }
        }
        if (!op.hasInputs() || op.getInputs().size() > 1) {
            return null;
        }
        return findNextUnnestOrJoin((AbstractLogicalOperator) op.getInputs().get(0).getValue(), innerUsedVars, outerUsedVars, innerOps, outerOps, topSelects);
    }
    
    private boolean findPlanPartition(AbstractLogicalOperator op, HashSet<LogicalVariable> innerUsedVars, HashSet<LogicalVariable> outerUsedVars,
            List<ILogicalOperator> innerOps, List<ILogicalOperator> outerOps, List<ILogicalOperator> topSelects) throws AlgebricksException {
        if (innerUsedVars.isEmpty()) {
            // Trivially joinable.
            return true;
        }
        switch (op.getOperatorTag()) {
            case UNNEST:
            case DATASOURCESCAN: {
                // We may have reached this state by descending through a subplan.
                outerOps.add(op);
                return true;
            }
            case INNERJOIN:
            case LEFTOUTERJOIN: {
                // Make sure that no variables that are live under this join are needed by the inner.
                List<LogicalVariable> liveVars = new ArrayList<LogicalVariable>();
                VariableUtilities.getLiveVariables(op, liveVars);
                for (LogicalVariable liveVar : liveVars) {
                    if (innerUsedVars.contains(liveVar)) {
                        return false;
                    }
                }
                outerOps.add(op);
                return true;
            }
            case SELECT: {
                // Remember this select to pulling it above the join.
                topSelects.add(op);
                break;
            }
            case PROJECT: {
                // Throw away projects from the plan since we are pulling selects up.
                break;
            }
            case EMPTYTUPLESOURCE: 
            case NESTEDTUPLESOURCE: {
                // We have successfully partitioned the plan into independent parts to be plugged into the join.
                return true;
            }
            default: {
                List<LogicalVariable> producedVars = new ArrayList<LogicalVariable>();
                VariableUtilities.getProducedVariables(op, producedVars);
                if (producedVars.isEmpty()) {
                    // TODO: We should not throw here, just for debugging.
                    throw new AlgebricksException("No produced variables in operator of type '" + op.getOperatorTag() + "'.");
                }
                int outerMatches = 0;
                int innerMatches = 0;                
                for (LogicalVariable producedVar : producedVars) {
                    if (outerUsedVars.contains(producedVar)) {
                        outerMatches++;
                    } else if (innerUsedVars.contains(producedVar)) {
                        innerMatches++;
                    }
                }
                
                HashSet<LogicalVariable> targetUsedVars = null;
                if (outerMatches == producedVars.size()) {
                    // Definitely part of the outer partition.
                    outerOps.add(op);
                    targetUsedVars = outerUsedVars;
                } else if (innerMatches == producedVars.size()) {
                    // Definitely part of the inner partition.
                    innerOps.add(op);
                    targetUsedVars = innerUsedVars;
                } else if (innerMatches == 0 && outerMatches == 0){
                    // Op produces a variable that is not used in the part of the plan we've seen. Try to figure out where it belongs by analyzing the used variables.
                    List<LogicalVariable> usedVars = new ArrayList<LogicalVariable>();
                    VariableUtilities.getUsedVariables(op, usedVars);
                    for (LogicalVariable usedVar : usedVars) {
                        if (outerUsedVars.contains(usedVar)) {
                            outerOps.add(op);
                            targetUsedVars = outerUsedVars;
                            break;
                        }
                        if (innerUsedVars.contains(usedVar)) {
                            innerOps.add(op);
                            targetUsedVars = innerUsedVars;
                            break;
                        }
                    }
                    if (targetUsedVars == null) {
                        return false;
                    }
                } else {
                    // The current operator produces variables that are used by both partitions, so the inner and outer are not independent and, therefore, we cannot create a join.
                    // TODO: We may still be able to split the operator to create a viable partitioning.
                    return false;
                }
                // Update used variables of partition that op belongs to.
                if (op.hasNestedPlans() && op.getOperatorTag() != LogicalOperatorTag.SUBPLAN) {
                    AbstractOperatorWithNestedPlans opWithNestedPlans = (AbstractOperatorWithNestedPlans) op;
                    opWithNestedPlans.getUsedVariablesExceptNestedPlans(targetUsedVars);
                } else {
                    VariableUtilities.getUsedVariables(op, targetUsedVars);
                }
                break;
            }
        }
        if (!op.hasInputs()) {
            // We have successfully partitioned the plan into independent parts to be plugged into the join.
            return true;
        }
        return findPlanPartition((AbstractLogicalOperator) op.getInputs().get(0).getValue(), innerUsedVars, outerUsedVars, innerOps, outerOps, topSelects);
    }
    
    /*
    private boolean findPlanPartition(HashSet<LogicalVariable> innerVars, HashSet<LogicalVariable> outerVars,
            Stack<ILogicalOperator> innerOps, Stack<ILogicalOperator> outerOps, AbstractLogicalOperator op) throws AlgebricksException {
        // Disregard unnests in a subplan.
        if (op.getOperatorTag() == LogicalOperatorTag.SUBPLAN) {
            return false;
        }
        if (op.getOperatorTag() == LogicalOperatorTag.DATASOURCESCAN
                || op.getOperatorTag() == LogicalOperatorTag.UNNEST
                || op.getInputs().size() > 1) {
            // Current op must belong to the outer.
            VariableUtilities.getLiveVariables(op, outerVars);
            outerOps.push(op);
            return true;
        }
        boolean success = false;
        if (op.hasInputs()) {
            success = findPlanPartition(innerVars, outerVars, innerOps, outerOps, (AbstractLogicalOperator) op.getInputs().get(0).getValue());
        }
        if (!success) {
            return false;
        }

        List<LogicalVariable> usedVars = new ArrayList<LogicalVariable>();        
        if (op.hasNestedPlans()) {
            AbstractOperatorWithNestedPlans opWithNestedPlans = (AbstractOperatorWithNestedPlans) op;
            opWithNestedPlans.getUsedVariablesExceptNestedPlans(usedVars);
        } else {
            VariableUtilities.getUsedVariables(op, usedVars);
        }
        
        int outerMatches = 0;
        for (LogicalVariable usedVar : usedVars) {
            if (outerVars.contains(usedVar)) {
                outerMatches++;
            }
        }
        
        if (outerMatches == usedVars.size()) {
            // Definitely part of the outer.
            VariableUtilities.getProducedVariables(op, outerVars);
            outerOps.push(op);
            return true;
        } else if (outerMatches == 0) {
            // Sanity check that all used vars are indeed in the inner partition.
            if (!innerVars.containsAll(usedVars)) {
                return false;
            }
            // Definitely part of the inner.
            VariableUtilities.getProducedVariables(op, innerVars);
            innerOps.push(op);
            return true;
        } else {
            // TODO: Some variables match the outer partition (and the others presumably match the inner,
            // otherwise there's a bigger problem).
            // Depending on the operator, we may be able to split it such that we create a viable partitioning.
            // For now just bail.
            return false;
        }
    }
    */
}
