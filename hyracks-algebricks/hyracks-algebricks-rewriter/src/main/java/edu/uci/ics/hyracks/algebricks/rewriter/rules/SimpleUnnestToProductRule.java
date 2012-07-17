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
import java.util.Stack;

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

        Mutable<ILogicalOperator> opRef2 = op.getInputs().get(0);
        AbstractLogicalOperator op2 = (AbstractLogicalOperator) opRef2.getValue();
        if (!descOrSelfIsUnnestOrJoin(op2)) {
            return false;
        }

        Stack<ILogicalOperator> innerOps = new Stack<ILogicalOperator>();
        Stack<ILogicalOperator> outerOps = new Stack<ILogicalOperator>();        
        HashSet<LogicalVariable> innerVars = new HashSet<LogicalVariable>();
        HashSet<LogicalVariable> outerVars = new HashSet<LogicalVariable>();        

        boolean partitionFound = findPlanPartition(innerVars, outerVars, innerOps, outerOps, op2);
        if (!partitionFound) {
            return false;
        }
        
        // The original op goes at the top of the inner operator chain.
        innerOps.push(op);
        // Make sure the top inner op does not use any vars of the outer plan partition.
        List<LogicalVariable> usedVars = new ArrayList<LogicalVariable>();
        VariableUtilities.getUsedVariables(op, usedVars);
        for (LogicalVariable usedVar : usedVars) {
            if (outerVars.contains(usedVar)) {
                return false;
            }
        }
        
        // Build the outer and inner operator chains that form the input for the join.
        ILogicalOperator outerRoot = buildOperatorChain(outerOps, false, context);
        ILogicalOperator innerRoot = buildOperatorChain(innerOps, true, context);
        
        InnerJoinOperator product = new InnerJoinOperator(
                new MutableObject<ILogicalExpression>(ConstantExpression.TRUE));
        // Outer branch.
        product.getInputs().add(new MutableObject<ILogicalOperator>(outerRoot));
        // Inner branch.
        product.getInputs().add(new MutableObject<ILogicalOperator>(innerRoot));
        // Plug the product in the plan.
        opRef.setValue(product);
        context.computeAndSetTypeEnvironmentForOperator(product);
        return true;
    }

    private ILogicalOperator buildOperatorChain(Stack<ILogicalOperator> ops, boolean etsAsSource, IOptimizationContext context) throws AlgebricksException {
        ILogicalOperator root = ops.pop();
        ILogicalOperator prevOp = root;
        while (!ops.isEmpty()) {
            ILogicalOperator inputOp = ops.pop();
            prevOp.getInputs().clear();
            prevOp.getInputs().add(new MutableObject<ILogicalOperator>(inputOp));
            prevOp = inputOp;
        }
        if (etsAsSource) {
            EmptyTupleSourceOperator ets = new EmptyTupleSourceOperator();
            context.computeAndSetTypeEnvironmentForOperator(ets);
            prevOp.getInputs().clear();
            prevOp.getInputs().add(new MutableObject<ILogicalOperator>(ets));
        }
        return root;
    }
    
    private boolean descOrSelfIsUnnestOrJoin(AbstractLogicalOperator op) {
        // Disregard unnests in a subplan.
        if (op.getOperatorTag() == LogicalOperatorTag.SUBPLAN) {
            return false;
        }
        if (op.getOperatorTag() == LogicalOperatorTag.DATASOURCESCAN
                || op.getOperatorTag() == LogicalOperatorTag.UNNEST
                || op.getInputs().size() > 1) {
            return true;
        }
        if (op.hasInputs()) {
            return descOrSelfIsUnnestOrJoin((AbstractLogicalOperator) op.getInputs().get(0).getValue());
        } else {
            return false;
        }
    }
    
    /**
     * Descend the operator tree looking for an UNNEST, DATASOURCESCAN or an operator with more than one input.
     * Such an operator will be the 'source' of the outer branch of the join.
     * We attempt to find a partitioning of the plan such that there are two independent operator chains,
     * one for the outer and one for the inner input of the join. 
     */
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
        
        int innerMatches = 0;
        for (LogicalVariable usedVar : usedVars) {
            if (outerVars.contains(usedVar)) {
                innerMatches++;
            }
        }
        
        if (innerMatches == usedVars.size()) {
            // Definitely part of the outer.
            VariableUtilities.getProducedVariables(op, outerVars);
            outerOps.push(op);
            return true;
        } else if (innerMatches == 0) {
            // Sanity check that all used vars are indeed in the inner partition.
            if (!innerVars.containsAll(usedVars)) {
                return false;
            }
            // Definitely part of the inner.
            VariableUtilities.getProducedVariables(op, innerVars);
            innerOps.push(op);
            return true;
        } else {
            // TODO: Some variables match the inner partition (and the others presumably match the outer,
            // otherwise there's a bigger problem).
            // Depending on the operator, we may be able to split it such that we create a viable partitioning.
            // For now just bail.
            return false;
        }
    }
}
