package edu.uci.ics.hyracks.algebricks.rewriter.rules;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.mutable.Mutable;

import edu.uci.ics.hyracks.algebricks.core.algebra.base.ILogicalOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.IOptimizationContext;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.PhysicalOperatorTag;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.AbstractLogicalOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.physical.HashPartitionExchangePOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.physical.HashPartitionMergeExchangePOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.physical.SortMergeExchangePOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.properties.OrderColumn;
import edu.uci.ics.hyracks.algebricks.core.api.exceptions.AlgebricksException;
import edu.uci.ics.hyracks.algebricks.core.rewriter.base.IAlgebraicRewriteRule;

public class IntroHashPartitionMergeExchange implements IAlgebraicRewriteRule {

    @Override
    public boolean rewritePre(Mutable<ILogicalOperator> opRef, IOptimizationContext context) throws AlgebricksException {
        return false;
    }

    @Override
    public boolean rewritePost(Mutable<ILogicalOperator> opRef, IOptimizationContext context)
            throws AlgebricksException {
        AbstractLogicalOperator op1 = (AbstractLogicalOperator) opRef.getValue();
        if (op1.getPhysicalOperator() == null
                || op1.getPhysicalOperator().getOperatorTag() != PhysicalOperatorTag.HASH_PARTITION_EXCHANGE) {
            return false;
        }
        AbstractLogicalOperator op2 = (AbstractLogicalOperator) op1.getInputs().get(0).getValue();
        if (op2.getPhysicalOperator() == null
                || op2.getPhysicalOperator().getOperatorTag() != PhysicalOperatorTag.SORT_MERGE_EXCHANGE) {
            return false;
        }
        HashPartitionExchangePOperator hpe = (HashPartitionExchangePOperator) op1.getPhysicalOperator();
        SortMergeExchangePOperator sme = (SortMergeExchangePOperator) op2.getPhysicalOperator();
        List<OrderColumn> ocList = new ArrayList<OrderColumn>();
        for (OrderColumn oc : sme.getSortColumns()) {
            ocList.add(oc);
        }
        HashPartitionMergeExchangePOperator hpme = new HashPartitionMergeExchangePOperator(ocList, hpe.getHashFields(),
                hpe.getDomain());
        op1.setPhysicalOperator(hpme);
        op1.getInputs().get(0).setValue(op2.getInputs().get(0).getValue());
        return true;
    }

}
