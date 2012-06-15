package edu.uci.ics.hyracks.algebricks.core.algebra.expressions;

import edu.uci.ics.hyracks.algebricks.common.exceptions.AlgebricksException;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.ILogicalExpression;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.IOperatorSchema;
import edu.uci.ics.hyracks.algebricks.core.jobgen.impl.JobGenContext;
import edu.uci.ics.hyracks.algebricks.runtime.base.IAggregateFunction;
import edu.uci.ics.hyracks.algebricks.runtime.base.IAggregateFunctionFactory;
import edu.uci.ics.hyracks.algebricks.runtime.base.ICopyAggregateFunction;
import edu.uci.ics.hyracks.algebricks.runtime.base.ICopyAggregateFunctionFactory;
import edu.uci.ics.hyracks.algebricks.runtime.base.ICopyEvaluator;
import edu.uci.ics.hyracks.algebricks.runtime.base.ICopyEvaluatorFactory;
import edu.uci.ics.hyracks.algebricks.runtime.base.ICopyRunningAggregateFunction;
import edu.uci.ics.hyracks.algebricks.runtime.base.ICopyRunningAggregateFunctionFactory;
import edu.uci.ics.hyracks.algebricks.runtime.base.ICopySerializableAggregateFunctionFactory;
import edu.uci.ics.hyracks.algebricks.runtime.base.ICopyUnnestingFunction;
import edu.uci.ics.hyracks.algebricks.runtime.base.ICopyUnnestingFunctionFactory;
import edu.uci.ics.hyracks.algebricks.runtime.base.IEvaluator;
import edu.uci.ics.hyracks.algebricks.runtime.base.IEvaluatorFactory;
import edu.uci.ics.hyracks.algebricks.runtime.base.IRunningAggregateFunction;
import edu.uci.ics.hyracks.algebricks.runtime.base.IRunningAggregateFunctionFactory;
import edu.uci.ics.hyracks.algebricks.runtime.base.IUnnestingFunction;
import edu.uci.ics.hyracks.algebricks.runtime.base.IUnnestingFunctionFactory;
import edu.uci.ics.hyracks.data.std.api.IPointable;
import edu.uci.ics.hyracks.dataflow.common.data.accessors.ArrayBackedValueStorage;
import edu.uci.ics.hyracks.dataflow.common.data.accessors.IFrameTupleReference;

public class LogicalExpressionJobGenToExpressionRuntimeProviderAdapter implements IExpressionRuntimeProvider {
    private final ILogicalExpressionJobGen lejg;

    public LogicalExpressionJobGenToExpressionRuntimeProviderAdapter(ILogicalExpressionJobGen lejg) {
        this.lejg = lejg;
    }

    @Override
    public IEvaluatorFactory createEvaluatorFactory(ILogicalExpression expr, IVariableTypeEnvironment env,
            IOperatorSchema[] inputSchemas, JobGenContext context) throws AlgebricksException {
        ICopyEvaluatorFactory cef = lejg.createEvaluatorFactory(expr, env, inputSchemas, context);
        return new EvaluatorFactoryAdapter(cef);
    }

    @Override
    public IAggregateFunctionFactory createAggregateFunctionFactory(AggregateFunctionCallExpression expr,
            IVariableTypeEnvironment env, IOperatorSchema[] inputSchemas, JobGenContext context)
            throws AlgebricksException {
        ICopyAggregateFunctionFactory caff = lejg.createAggregateFunctionFactory(expr, env, inputSchemas, context);
        return new AggregateFunctionFactoryAdapter(caff);
    }

    @Override
    public ICopySerializableAggregateFunctionFactory createSerializableAggregateFunctionFactory(
            AggregateFunctionCallExpression expr, IVariableTypeEnvironment env, IOperatorSchema[] inputSchemas,
            JobGenContext context) throws AlgebricksException {
        return lejg.createSerializableAggregateFunctionFactory(expr, env, inputSchemas, context);
    }

    @Override
    public IRunningAggregateFunctionFactory createRunningAggregateFunctionFactory(StatefulFunctionCallExpression expr,
            IVariableTypeEnvironment env, IOperatorSchema[] inputSchemas, JobGenContext context)
            throws AlgebricksException {
        ICopyRunningAggregateFunctionFactory craff = lejg.createRunningAggregateFunctionFactory(expr, env,
                inputSchemas, context);
        return new RunningAggregateFunctionFactoryAdapter(craff);
    }

    @Override
    public IUnnestingFunctionFactory createUnnestingFunctionFactory(UnnestingFunctionCallExpression expr,
            IVariableTypeEnvironment env, IOperatorSchema[] inputSchemas, JobGenContext context)
            throws AlgebricksException {
        ICopyUnnestingFunctionFactory cuff = lejg.createUnnestingFunctionFactory(expr, env, inputSchemas, context);
        return new UnnestingFunctionFactoryAdapter(cuff);
    }

    private static final class EvaluatorFactoryAdapter implements IEvaluatorFactory {
        private static final long serialVersionUID = 1L;

        private final ICopyEvaluatorFactory cef;

        public EvaluatorFactoryAdapter(ICopyEvaluatorFactory cef) {
            this.cef = cef;
        }

        @Override
        public IEvaluator createEvaluator() throws AlgebricksException {
            final ArrayBackedValueStorage abvs = new ArrayBackedValueStorage();
            final ICopyEvaluator ce = cef.createEvaluator(abvs);
            return new IEvaluator() {
                @Override
                public void evaluate(IFrameTupleReference tuple, IPointable result) throws AlgebricksException {
                    abvs.reset();
                    ce.evaluate(tuple);
                    result.set(abvs);
                }
            };
        }
    }

    private static final class AggregateFunctionFactoryAdapter implements IAggregateFunctionFactory {
        private static final long serialVersionUID = 1L;

        private final ICopyAggregateFunctionFactory caff;

        public AggregateFunctionFactoryAdapter(ICopyAggregateFunctionFactory caff) {
            this.caff = caff;
        }

        @Override
        public IAggregateFunction createAggregateFunction() throws AlgebricksException {
            final ArrayBackedValueStorage abvs = new ArrayBackedValueStorage();
            final ICopyAggregateFunction caf = caff.createAggregateFunction(abvs);
            return new IAggregateFunction() {
                @Override
                public void step(IFrameTupleReference tuple) throws AlgebricksException {
                    caf.step(tuple);
                }

                @Override
                public void init() throws AlgebricksException {
                    abvs.reset();
                    caf.init();
                }

                @Override
                public void finishPartial() throws AlgebricksException {
                    caf.finishPartial();
                }

                @Override
                public void finish(IPointable result) throws AlgebricksException {
                    caf.finish();
                    result.set(abvs);
                }
            };
        }
    }

    private static final class RunningAggregateFunctionFactoryAdapter implements IRunningAggregateFunctionFactory {
        private static final long serialVersionUID = 1L;

        private final ICopyRunningAggregateFunctionFactory craff;

        public RunningAggregateFunctionFactoryAdapter(ICopyRunningAggregateFunctionFactory craff) {
            this.craff = craff;
        }

        @Override
        public IRunningAggregateFunction createRunningAggregateFunction() throws AlgebricksException {
            final ArrayBackedValueStorage abvs = new ArrayBackedValueStorage();
            final ICopyRunningAggregateFunction craf = craff.createRunningAggregateFunction(abvs);
            return new IRunningAggregateFunction() {
                @Override
                public void step(IFrameTupleReference tuple, IPointable result) throws AlgebricksException {
                    abvs.reset();
                    craf.step(tuple);
                    result.set(abvs);
                }

                @Override
                public void init() throws AlgebricksException {
                    craf.init();
                }
            };
        }
    }

    private static final class UnnestingFunctionFactoryAdapter implements IUnnestingFunctionFactory {
        private static final long serialVersionUID = 1L;

        private final ICopyUnnestingFunctionFactory cuff;

        public UnnestingFunctionFactoryAdapter(ICopyUnnestingFunctionFactory cuff) {
            this.cuff = cuff;
        }

        @Override
        public IUnnestingFunction createUnnestingFunction() throws AlgebricksException {
            final ArrayBackedValueStorage abvs = new ArrayBackedValueStorage();
            final ICopyUnnestingFunction cuf = cuff.createUnnestingFunction(abvs);
            return new IUnnestingFunction() {
                @Override
                public boolean step(IPointable result) throws AlgebricksException {
                    abvs.reset();
                    if (cuf.step()) {
                        result.set(abvs);
                        return true;
                    }
                    return false;
                }

                @Override
                public void init(IFrameTupleReference tuple) throws AlgebricksException {
                    abvs.reset();
                }
            };
        }
    }
}