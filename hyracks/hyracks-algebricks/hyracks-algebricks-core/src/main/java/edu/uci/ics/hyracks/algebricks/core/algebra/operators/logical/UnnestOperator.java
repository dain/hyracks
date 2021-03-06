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
package edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical;

import java.util.ArrayList;

import org.apache.commons.lang3.mutable.Mutable;

import edu.uci.ics.hyracks.algebricks.core.algebra.base.ILogicalExpression;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.LogicalOperatorTag;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.LogicalVariable;
import edu.uci.ics.hyracks.algebricks.core.algebra.expressions.IVariableTypeEnvironment;
import edu.uci.ics.hyracks.algebricks.core.algebra.typing.ITypingContext;
import edu.uci.ics.hyracks.algebricks.core.algebra.visitors.ILogicalOperatorVisitor;
import edu.uci.ics.hyracks.algebricks.core.api.exceptions.AlgebricksException;

public class UnnestOperator extends AbstractUnnestOperator {

    private LogicalVariable positionalVariable;
    private Object positionalVariableType;

    public UnnestOperator(LogicalVariable variable, Mutable<ILogicalExpression> expression) {
        super(makeSingletonList(variable), expression);
    }

    public UnnestOperator(LogicalVariable variable, Mutable<ILogicalExpression> expression,
            LogicalVariable positionalVariable, Object positionalVariableType) {
        this(variable, expression);
        this.setPositionalVariable(positionalVariable);
        this.setPositionalVariableType(positionalVariableType);
    }

    @Override
    public LogicalOperatorTag getOperatorTag() {
        return LogicalOperatorTag.UNNEST;
    }

    public LogicalVariable getVariable() {
        return variables.get(0);
    }

    public void setPositionalVariable(LogicalVariable positionalVariable) {
        this.positionalVariable = positionalVariable;
    }

    public LogicalVariable getPositionalVariable() {
        return positionalVariable;
    }

    public void setPositionalVariableType(Object positionalVariableType) {
        this.positionalVariableType = positionalVariableType;
    }

    public Object getPositionalVariableType() {
        return positionalVariableType;
    }

    @Override
    public <R, T> R accept(ILogicalOperatorVisitor<R, T> visitor, T arg) throws AlgebricksException {
        return visitor.visitUnnestOperator(this, arg);
    }

    private static <E> ArrayList<E> makeSingletonList(E item) {
        ArrayList<E> array = new ArrayList<E>(1);
        array.add(item);
        return array;
    }

    @Override
    public IVariableTypeEnvironment computeOutputTypeEnvironment(ITypingContext ctx) throws AlgebricksException {
        IVariableTypeEnvironment env = createPropagatingAllInputsTypeEnvironment(ctx);
        Object t = env.getType(expression.getValue());
        env.setVarType(variables.get(0), t);
        if (positionalVariable != null) {
            env.setVarType(positionalVariable, positionalVariableType);
        }
        return env;
    }
}