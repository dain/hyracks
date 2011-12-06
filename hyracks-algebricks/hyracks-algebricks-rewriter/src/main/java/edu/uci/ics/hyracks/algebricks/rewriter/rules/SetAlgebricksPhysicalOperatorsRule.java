package edu.uci.ics.hyracks.algebricks.rewriter.rules;

import java.util.ArrayList;
import java.util.List;

import edu.uci.ics.hyracks.algebricks.core.algebra.base.ILogicalExpression;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.ILogicalPlan;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.IOptimizationContext;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.LogicalExpressionReference;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.LogicalExpressionTag;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.LogicalOperatorReference;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.LogicalVariable;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.OperatorAnnotations;
import edu.uci.ics.hyracks.algebricks.core.algebra.expressions.IMergeAggregationExpressionFactory;
import edu.uci.ics.hyracks.algebricks.core.algebra.expressions.VariableReferenceExpression;
import edu.uci.ics.hyracks.algebricks.core.algebra.metadata.IDataSource;
import edu.uci.ics.hyracks.algebricks.core.algebra.metadata.IMetadataProvider;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.AbstractLogicalOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.AbstractOperatorWithNestedPlans;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.AggregateOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.DataSourceScanOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.DistinctOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.GroupByOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.IndexInsertDeleteOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.InnerJoinOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.InsertDeleteOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.LeftOuterJoinOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.LimitOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.OrderOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.WriteResultOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.AbstractLogicalOperator.ExecutionMode;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.OrderOperator.IOrder;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.physical.AggregatePOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.physical.AssignPOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.physical.DataSourceScanPOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.physical.EmptyTupleSourcePOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.physical.ExternalGroupByPOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.physical.InMemoryStableSortPOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.physical.IndexInsertDeletePOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.physical.InsertDeletePOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.physical.MicroPreclusteredGroupByPOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.physical.NestedTupleSourcePOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.physical.PreSortedDistinctByPOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.physical.PreclusteredGroupByPOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.physical.ReplicatePOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.physical.RunningAggregatePOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.physical.SinkPOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.physical.SinkWritePOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.physical.StableSortPOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.physical.StreamDiePOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.physical.StreamLimitPOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.physical.StreamProjectPOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.physical.StreamSelectPOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.physical.StringStreamingScriptPOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.physical.SubplanPOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.physical.UnionAllPOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.physical.UnnestPOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.physical.WriteResultPOperator;
import edu.uci.ics.hyracks.algebricks.core.api.exceptions.AlgebricksException;
import edu.uci.ics.hyracks.algebricks.core.api.exceptions.NotImplementedException;
import edu.uci.ics.hyracks.algebricks.core.rewriter.base.IAlgebraicRewriteRule;
import edu.uci.ics.hyracks.algebricks.core.rewriter.base.PhysicalOptimizationConfig;
import edu.uci.ics.hyracks.algebricks.core.utils.Pair;
import edu.uci.ics.hyracks.algebricks.rewriter.util.JoinUtils;

public class SetAlgebricksPhysicalOperatorsRule implements IAlgebraicRewriteRule {

    @Override
    public boolean rewritePost(LogicalOperatorReference opRef, IOptimizationContext context) throws AlgebricksException {
        return false;
    }

    @Override
    public boolean rewritePre(LogicalOperatorReference opRef, IOptimizationContext context) throws AlgebricksException {
        AbstractLogicalOperator op = (AbstractLogicalOperator) opRef.getOperator();
        // if (context.checkIfInDontApplySet(this, op)) {
        // return false;
        // }
        if (op.getPhysicalOperator() != null) {
            return false;
        }

        computeDefaultPhysicalOp(op, true, context);
        // context.addToDontApplySet(this, op);
        return true;
    }

    private static void setPhysicalOperators(ILogicalPlan plan, boolean topLevelOp, IOptimizationContext context)
            throws AlgebricksException {
        for (LogicalOperatorReference root : plan.getRoots()) {
            computeDefaultPhysicalOp((AbstractLogicalOperator) root.getOperator(), topLevelOp, context);
        }
    }

    @SuppressWarnings("unchecked")
    private static void computeDefaultPhysicalOp(AbstractLogicalOperator op, boolean topLevelOp,
            IOptimizationContext context) throws AlgebricksException {
        PhysicalOptimizationConfig physicalOptimizationConfig = context.getPhysicalOptimizationConfig();
        if (op.getPhysicalOperator() == null) {
            switch (op.getOperatorTag()) {
                case AGGREGATE: {
                    op.setPhysicalOperator(new AggregatePOperator());
                    break;
                }
                case ASSIGN: {
                    op.setPhysicalOperator(new AssignPOperator());
                    break;
                }
                case DISTINCT: {
                    DistinctOperator distinct = (DistinctOperator) op;
                    distinct.setPhysicalOperator(new PreSortedDistinctByPOperator(distinct.getDistinctByVarList()));
                    break;
                }
                case EMPTYTUPLESOURCE: {
                    op.setPhysicalOperator(new EmptyTupleSourcePOperator());
                    break;
                }
                case EXCHANGE: {
                    if (op.getPhysicalOperator() == null) {
                        throw new AlgebricksException("Implementation for EXCHANGE operator was not set.");
                    }
                    // implem. choice for exchange should be set by a parent op.
                    break;
                }
                case GROUP: {
                    GroupByOperator gby = (GroupByOperator) op;

                    if (gby.getNestedPlans().size() == 1) {
                        ILogicalPlan p0 = gby.getNestedPlans().get(0);
                        if (p0.getRoots().size() == 1) {
                            if (gby.getAnnotations().get(OperatorAnnotations.USE_HASH_GROUP_BY) == Boolean.TRUE
                                    || gby.getAnnotations().get(OperatorAnnotations.USE_EXTERNAL_GROUP_BY) == Boolean.TRUE) {
                                if (!topLevelOp) {
                                    throw new NotImplementedException(
                                            "External hash group-by for nested grouping is not implemented.");
                                }
                                ExternalGroupByPOperator externalGby = new ExternalGroupByPOperator(
                                        gby.getGroupByList(), physicalOptimizationConfig.getMaxFramesExternalGroupBy(),
                                        physicalOptimizationConfig.getExternalGroupByTableSize());
                                op.setPhysicalOperator(externalGby);
                                generateMergeAggregationExpressions(gby, context);
                                break;
                            }
                        }
                    }

                    List<Pair<LogicalVariable, LogicalExpressionReference>> gbyList = gby.getGroupByList();
                    List<LogicalVariable> columnList = new ArrayList<LogicalVariable>(gbyList.size());
                    for (Pair<LogicalVariable, LogicalExpressionReference> p : gbyList) {
                        ILogicalExpression expr = p.second.getExpression();
                        if (expr.getExpressionTag() == LogicalExpressionTag.VARIABLE) {
                            VariableReferenceExpression varRef = (VariableReferenceExpression) expr;
                            columnList.add(varRef.getVariableReference());
                        }
                    }
                    if (topLevelOp) {
                        op.setPhysicalOperator(new PreclusteredGroupByPOperator(columnList));
                    } else {
                        op.setPhysicalOperator(new MicroPreclusteredGroupByPOperator(columnList));
                    }
                    break;
                }
                case INNERJOIN: {
                    JoinUtils.setJoinAlgorithmAndExchangeAlgo((InnerJoinOperator) op, context);
                    break;
                }
                case LEFTOUTERJOIN: {
                    JoinUtils.setJoinAlgorithmAndExchangeAlgo((LeftOuterJoinOperator) op, context);
                    break;
                }
                case LIMIT: {
                    LimitOperator opLim = (LimitOperator) op;
                    op.setPhysicalOperator(new StreamLimitPOperator(opLim.isTopmostLimitOp()
                            && opLim.getExecutionMode() == ExecutionMode.PARTITIONED));
                    break;
                }
                case NESTEDTUPLESOURCE: {
                    op.setPhysicalOperator(new NestedTupleSourcePOperator());
                    break;
                }
                case ORDER: {
                    OrderOperator oo = (OrderOperator) op;
                    for (Pair<IOrder, LogicalExpressionReference> p : oo.getOrderExpressions()) {
                        ILogicalExpression e = p.second.getExpression();
                        if (e.getExpressionTag() != LogicalExpressionTag.VARIABLE) {
                            throw new AlgebricksException("Order expression " + e + " has not been normalized.");
                        }
                    }
                    if (topLevelOp) {
                        op.setPhysicalOperator(new StableSortPOperator(physicalOptimizationConfig
                                .getMaxFramesExternalSort()));
                    } else {
                        op.setPhysicalOperator(new InMemoryStableSortPOperator());
                    }
                    break;
                }
                case PROJECT: {
                    op.setPhysicalOperator(new StreamProjectPOperator());
                    break;
                }
                case RUNNINGAGGREGATE: {
                    op.setPhysicalOperator(new RunningAggregatePOperator());
                    break;
                }
                case REPLICATE: {
                    op.setPhysicalOperator(new ReplicatePOperator());
                    break;
                }
                case SCRIPT: {
                    op.setPhysicalOperator(new StringStreamingScriptPOperator());
                    break;
                }
                case SELECT: {
                    op.setPhysicalOperator(new StreamSelectPOperator());
                    break;
                }
                case SUBPLAN: {
                    op.setPhysicalOperator(new SubplanPOperator());
                    break;
                }
                case UNIONALL: {
                    op.setPhysicalOperator(new UnionAllPOperator());
                    break;
                }

                case UNNEST: {
                    op.setPhysicalOperator(new UnnestPOperator());
                    break;
                }
                case DATASOURCESCAN: {
                    DataSourceScanOperator scan = (DataSourceScanOperator) op;
                    IDataSource dataSource = scan.getDataSource();
                    DataSourceScanPOperator dss = new DataSourceScanPOperator(dataSource);
                    IMetadataProvider mp = context.getMetadataProvider();
                    if (mp.scannerOperatorIsLeaf(dataSource)) {
                        dss.disableJobGenBelowMe();
                    }
                    op.setPhysicalOperator(dss);
                    break;
                }
                case WRITE: {
                    op.setPhysicalOperator(new SinkWritePOperator());
                    break;
                }
                case WRITE_RESULT: {
                    WriteResultOperator opLoad = (WriteResultOperator) op;
                    LogicalVariable payload;
                    List<LogicalVariable> keys = new ArrayList<LogicalVariable>();
                    payload = getKeysAndLoad(opLoad.getPayloadExpression(), opLoad.getKeyExpressions(), keys);
                    op.setPhysicalOperator(new WriteResultPOperator(opLoad.getDataSource(), payload, keys));
                    break;
                }
                case INSERT_DELETE: {
                    InsertDeleteOperator opLoad = (InsertDeleteOperator) op;
                    LogicalVariable payload;
                    List<LogicalVariable> keys = new ArrayList<LogicalVariable>();
                    payload = getKeysAndLoad(opLoad.getPayloadExpression(), opLoad.getPrimaryKeyExpressions(), keys);
                    op.setPhysicalOperator(new InsertDeletePOperator(payload, keys, opLoad.getDataSource()));
                    break;
                }
                case INDEX_INSERT_DELETE: {
                    IndexInsertDeleteOperator opLoad = (IndexInsertDeleteOperator) op;
                    List<LogicalVariable> primaryKeys = new ArrayList<LogicalVariable>();
                    List<LogicalVariable> secondaryKeys = new ArrayList<LogicalVariable>();
                    getKeys(opLoad.getPrimaryKeyExpressions(), primaryKeys);
                    getKeys(opLoad.getSecondaryKeyExpressions(), secondaryKeys);
                    op.setPhysicalOperator(new IndexInsertDeletePOperator(primaryKeys, secondaryKeys, opLoad
                            .getDataSourceIndex()));
                    break;
                }
                case SINK: {
                    op.setPhysicalOperator(new SinkPOperator());
                    break;
                }
                case DIE: {
                    op.setPhysicalOperator(new StreamDiePOperator());
                    break;
                }
            }
        }
        if (op.hasNestedPlans()) {
            AbstractOperatorWithNestedPlans nested = (AbstractOperatorWithNestedPlans) op;
            for (ILogicalPlan p : nested.getNestedPlans()) {
                setPhysicalOperators(p, false, context);
            }
        }
        for (LogicalOperatorReference opRef : op.getInputs()) {
            computeDefaultPhysicalOp((AbstractLogicalOperator) opRef.getOperator(), topLevelOp, context);
        }
    }

    private static void getKeys(List<LogicalExpressionReference> keyExpressions, List<LogicalVariable> keys) {
        for (LogicalExpressionReference kExpr : keyExpressions) {
            ILogicalExpression e = kExpr.getExpression();
            if (e.getExpressionTag() != LogicalExpressionTag.VARIABLE) {
                throw new NotImplementedException();
            }
            keys.add(((VariableReferenceExpression) e).getVariableReference());
        }
    }

    private static LogicalVariable getKeysAndLoad(LogicalExpressionReference payloadExpr,
            List<LogicalExpressionReference> keyExpressions, List<LogicalVariable> keys) {
        LogicalVariable payload;
        if (payloadExpr.getExpression().getExpressionTag() != LogicalExpressionTag.VARIABLE) {
            throw new NotImplementedException();
        }
        payload = ((VariableReferenceExpression) payloadExpr.getExpression()).getVariableReference();

        for (LogicalExpressionReference kExpr : keyExpressions) {
            ILogicalExpression e = kExpr.getExpression();
            if (e.getExpressionTag() != LogicalExpressionTag.VARIABLE) {
                throw new NotImplementedException();
            }
            keys.add(((VariableReferenceExpression) e).getVariableReference());
        }
        return payload;
    }

    private static void generateMergeAggregationExpressions(GroupByOperator gby, IOptimizationContext context)
            throws AlgebricksException {
        if (gby.getNestedPlans().size() != 1) {
            throw new AlgebricksException(
                    "External group-by currently works only for one nested plan with one root containing"
                            + "an aggregate and a nested-tuple-source.");
        }
        ILogicalPlan p0 = gby.getNestedPlans().get(0);
        if (p0.getRoots().size() != 1) {
            throw new AlgebricksException(
                    "External group-by currently works only for one nested plan with one root containing"
                            + "an aggregate and a nested-tuple-source.");
        }
        IMergeAggregationExpressionFactory mergeAggregationExpressionFactory = context
                .getMergeAggregationExpressionFactory();
        LogicalOperatorReference r0 = p0.getRoots().get(0);
        AggregateOperator aggOp = (AggregateOperator) r0.getOperator();
        List<LogicalExpressionReference> aggFuncRefs = aggOp.getExpressions();
        int n = aggOp.getExpressions().size();
        List<LogicalExpressionReference> mergeExpressionRefs = new ArrayList<LogicalExpressionReference>();
        for (int i = 0; i < n; i++) {
            ILogicalExpression mergeExpr = mergeAggregationExpressionFactory.createMergeAggregation(aggFuncRefs.get(i)
                    .getExpression(), context);
            mergeExpressionRefs.add(new LogicalExpressionReference(mergeExpr));
        }
        aggOp.setMergeExpressions(mergeExpressionRefs);
    }
}
