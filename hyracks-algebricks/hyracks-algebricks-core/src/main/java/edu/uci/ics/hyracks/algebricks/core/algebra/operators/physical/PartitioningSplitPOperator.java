package edu.uci.ics.hyracks.algebricks.core.algebra.operators.physical;

import java.util.List;

import org.apache.commons.lang3.mutable.Mutable;

import edu.uci.ics.hyracks.algebricks.common.exceptions.AlgebricksException;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.IHyracksJobBuilder;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.ILogicalExpression;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.ILogicalOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.IOptimizationContext;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.PhysicalOperatorTag;
import edu.uci.ics.hyracks.algebricks.core.algebra.expressions.ILogicalExpressionJobGen;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.AbstractLogicalOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.IOperatorSchema;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.PartitioningSplitOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.properties.IPhysicalPropertiesVector;
import edu.uci.ics.hyracks.algebricks.core.algebra.properties.PhysicalRequirements;
import edu.uci.ics.hyracks.algebricks.core.algebra.properties.StructuralPropertiesVector;
import edu.uci.ics.hyracks.algebricks.core.jobgen.impl.JobGenContext;
import edu.uci.ics.hyracks.algebricks.core.jobgen.impl.JobGenHelper;
import edu.uci.ics.hyracks.algebricks.runtime.base.ICopyEvaluatorFactory;
import edu.uci.ics.hyracks.algebricks.runtime.operators.std.PartitioningSplitOperatorDescriptor;
import edu.uci.ics.hyracks.api.dataflow.value.RecordDescriptor;

public class PartitioningSplitPOperator extends AbstractPhysicalOperator {

    @Override
    public PhysicalOperatorTag getOperatorTag() {
        return PhysicalOperatorTag.PARTITIONINGSPLIT;
    }

    @Override
    public boolean isMicroOperator() {
        return false;
    }
    
    @Override
    public PhysicalRequirements getRequiredPropertiesForChildren(ILogicalOperator op,
            IPhysicalPropertiesVector reqdByParent) {
        return emptyUnaryRequirements();
    }

    @Override
    public void computeDeliveredProperties(ILogicalOperator op, IOptimizationContext context) {
        AbstractLogicalOperator op2 = (AbstractLogicalOperator) op.getInputs().get(0).getValue();
        deliveredProperties = (StructuralPropertiesVector) op2.getDeliveredPhysicalProperties().clone();
    }

    @Override
    public void contributeRuntimeOperator(IHyracksJobBuilder builder, JobGenContext context, ILogicalOperator op,
            IOperatorSchema opSchema, IOperatorSchema[] inputSchemas, IOperatorSchema outerPlanSchema)
            throws AlgebricksException {
        PartitioningSplitOperator partSplitOp = (PartitioningSplitOperator) op;
        List<Mutable<ILogicalExpression>> expressions = partSplitOp.getExpressions();
        ICopyEvaluatorFactory[] evalFactories = new ICopyEvaluatorFactory[expressions.size()];
        ILogicalExpressionJobGen exprJobGen = context.getExpressionJobGen();
        for (int i = 0; i < evalFactories.length; i++) {
            evalFactories[i] = exprJobGen.createEvaluatorFactory(expressions.get(i).getValue(),
                    context.getTypeEnvironment(op.getInputs().get(0).getValue()), inputSchemas, context);
        }
        RecordDescriptor recDesc = JobGenHelper.mkRecordDescriptor(context.getTypeEnvironment(op), opSchema, context);
        PartitioningSplitOperatorDescriptor partSplitOpDesc = new PartitioningSplitOperatorDescriptor(
                builder.getJobSpec(), evalFactories, context.getBinaryBooleanInspector(),
                partSplitOp.getDefaultBranchIndex(), recDesc);
        contributeOpDesc(builder, partSplitOp, partSplitOpDesc);
        ILogicalOperator srcExchange = partSplitOp.getInputs().get(0).getValue();
        builder.contributeGraphEdge(srcExchange, 0, partSplitOp, 0);
    }

}
