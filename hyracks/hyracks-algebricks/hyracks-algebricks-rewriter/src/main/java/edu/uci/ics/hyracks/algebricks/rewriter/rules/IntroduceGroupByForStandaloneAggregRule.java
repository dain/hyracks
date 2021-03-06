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
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang3.mutable.Mutable;
import org.apache.commons.lang3.mutable.MutableObject;

import edu.uci.ics.hyracks.algebricks.core.algebra.base.ILogicalExpression;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.ILogicalOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.ILogicalPlan;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.IOptimizationContext;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.LogicalOperatorTag;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.LogicalVariable;
import edu.uci.ics.hyracks.algebricks.core.algebra.expressions.ConstantExpression;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.AbstractLogicalOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.AggregateOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.AssignOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.GroupByOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.NestedTupleSourceOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.visitors.VariableUtilities;
import edu.uci.ics.hyracks.algebricks.core.algebra.plan.ALogicalPlanImpl;
import edu.uci.ics.hyracks.algebricks.core.api.exceptions.AlgebricksException;
import edu.uci.ics.hyracks.algebricks.core.rewriter.base.IAlgebraicRewriteRule;
import edu.uci.ics.hyracks.algebricks.core.utils.Pair;

/**
 * 
 * When aggregates appear w/o group-by, a default group by a constant is
 * introduced.
 * 
 */

public class IntroduceGroupByForStandaloneAggregRule implements IAlgebraicRewriteRule {

    @Override
    public boolean rewritePre(Mutable<ILogicalOperator> opRef, IOptimizationContext context) throws AlgebricksException {
        return false;
    }

    @Override
    public boolean rewritePost(Mutable<ILogicalOperator> opRef, IOptimizationContext context)
            throws AlgebricksException {
        AbstractLogicalOperator op = (AbstractLogicalOperator) opRef.getValue();
        if (op.getOperatorTag() != LogicalOperatorTag.ASSIGN) {
            return false;
        }
        Mutable<ILogicalOperator> opRef2 = op.getInputs().get(0);
        AbstractLogicalOperator op2 = (AbstractLogicalOperator) opRef2.getValue();
        if (op2.getOperatorTag() != LogicalOperatorTag.AGGREGATE) {
            return false;
        }

        AssignOperator assign = (AssignOperator) op;
        AggregateOperator agg = (AggregateOperator) op2;
        if (agg.getVariables().size() != 1) {
            return false;
        }
        LogicalVariable aggVar = agg.getVariables().get(0);
        List<LogicalVariable> used = new LinkedList<LogicalVariable>();
        VariableUtilities.getUsedVariables(assign, used);
        if (used.contains(aggVar)) {
            Mutable<ILogicalOperator> opRef3 = op2.getInputs().get(0);
            List<Pair<LogicalVariable, Mutable<ILogicalExpression>>> groupByList = new ArrayList<Pair<LogicalVariable, Mutable<ILogicalExpression>>>();
            LogicalVariable gbyVar = context.newVar();
            // ILogicalExpression constOne = new ConstantExpression(new
            // IntegerLiteral(new Integer(1)));
            groupByList.add(new Pair<LogicalVariable, Mutable<ILogicalExpression>>(gbyVar,
                    new MutableObject<ILogicalExpression>(ConstantExpression.TRUE)));
            NestedTupleSourceOperator nts = new NestedTupleSourceOperator(new MutableObject<ILogicalOperator>());
            List<Mutable<ILogicalOperator>> aggInpList = agg.getInputs();
            aggInpList.clear();
            aggInpList.add(new MutableObject<ILogicalOperator>(nts));
            ILogicalPlan np1 = new ALogicalPlanImpl(opRef2);
            ArrayList<ILogicalPlan> nestedPlans = new ArrayList<ILogicalPlan>();
            nestedPlans.add(np1);
            GroupByOperator gbyOp = new GroupByOperator(groupByList,
                    new ArrayList<Pair<LogicalVariable, Mutable<ILogicalExpression>>>(), nestedPlans);
            Mutable<ILogicalOperator> opRefGby = new MutableObject<ILogicalOperator>(gbyOp);
            nts.getDataSourceReference().setValue(gbyOp);
            gbyOp.getInputs().add(opRef3);
            List<Mutable<ILogicalOperator>> asgnInpList = assign.getInputs();
            context.computeAndSetTypeEnvironmentForOperator(nts);
            context.computeAndSetTypeEnvironmentForOperator(agg);
            context.computeAndSetTypeEnvironmentForOperator(gbyOp);
            asgnInpList.clear();
            asgnInpList.add(opRefGby);
            return true;
        }
        return false;
    }

}
