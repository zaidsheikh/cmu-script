package edu.cmu.cs.lti.script.debug;

import com.google.common.collect.BiMap;
import edu.cmu.cs.lti.script.utils.DataPool;
import edu.cmu.cs.lti.model.MutableDouble;
import edu.cmu.cs.lti.utils.BitUtils;
import edu.cmu.cs.lti.utils.Configuration;
import edu.cmu.cs.lti.utils.TLongBasedFeatureTable;
import gnu.trove.iterator.TLongObjectIterator;
import gnu.trove.iterator.TObjectDoubleIterator;
import gnu.trove.map.TObjectDoubleMap;
import gnu.trove.map.hash.TObjectDoubleHashMap;
import org.apache.commons.lang3.tuple.Pair;
import weka.core.SerializationHelper;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.Map;
import java.util.TreeMap;

/**
 * Created with IntelliJ IDEA.
 * User: zhengzhongliu
 * Date: 11/5/14
 * Time: 2:46 PM
 */
public class ModelChecker {

    private void checkCompactModel(Configuration config) throws Exception {
        String modelPath = config.get("edu.cmu.cs.lti.cds.negative.model.testing.path");

        System.out.println("Loading from " + modelPath);
        TLongBasedFeatureTable compactWeights = (TLongBasedFeatureTable) SerializationHelper.read(modelPath);

        String[] dbNames = config.getList("edu.cmu.cs.lti.cds.db.basenames"); //db names;
        String dbPath = config.get("edu.cmu.cs.lti.cds.dbpath"); //"dbpath"
//        String[] countingDbFileNames = config.getList("edu.edu.cmu.cs.lti.cds.headcount.files");

        DataPool.loadHeadStatistics(config, false);

        BiMap<Short, String> featureNames = compactWeights.getFeatureNameMap();

        File out = new File("model_out");

        BufferedWriter writer = new BufferedWriter(new FileWriter(out));

        int numFeatures = 0;
        int numLexicalPairs = 0;
        for (TLongObjectIterator<TreeMap<Short, MutableDouble>> rowIter = compactWeights.iterator(); rowIter.hasNext(); ) {
            rowIter.advance();
            numLexicalPairs++;
            Pair<Integer, Integer> wordIds = BitUtils.get2IntFromLong(rowIter.key());
            writer.write(DataPool.headWords[wordIds.getKey()] + " " + DataPool.headWords[wordIds.getValue()] + " " + wordIds.getKey() + " " + wordIds.getValue() + "\n");

            for (Map.Entry<Short, MutableDouble> entry : rowIter.value().entrySet()) {
                writer.write("\t" + featureNames.get(entry.getKey()) + " " + entry.getKey() + " " + entry.getValue() + "\n");
            }
            for (Map.Entry<Short, MutableDouble> cell : rowIter.value().entrySet()) {
                writer.write("\t" + featureNames.get(cell.getKey()) + " " + cell.getKey() + " " + cell.getValue() + "\n");
                numFeatures++;
            }
        }
        System.out.println("Number of lexical pairs " + numLexicalPairs + " , num of features " + numFeatures);
        writer.close();
    }

    private void checkHashModel(Configuration config) throws Exception {
        String modelPath = config.get("edu.cmu.cs.lti.cds.negative.model.testing.path");
        TObjectDoubleMap<String> weights = (TObjectDoubleHashMap<String>) SerializationHelper.read(modelPath);

        File out = new File("model_out");

        BufferedWriter writer = new BufferedWriter(new FileWriter(out));

        for (TObjectDoubleIterator<String> iter = weights.iterator(); iter.hasNext(); ) {
            iter.advance();
//            System.out.println(iter.key() + " " + iter.value());
            writer.write(iter.key() + " " + iter.value() + "\n");
        }
    }

    public static void main(String[] args) throws Exception {
        Configuration config = new Configuration(new File(args[0]));
        ModelChecker c = new ModelChecker();
        c.checkCompactModel(config);
    }
}