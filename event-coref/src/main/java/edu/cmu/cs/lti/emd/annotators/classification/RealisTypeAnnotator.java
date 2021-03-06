package edu.cmu.cs.lti.emd.annotators.classification;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import edu.cmu.cs.lti.collection_reader.TbfEventDataReader;
import edu.cmu.cs.lti.learning.feature.FeatureSpecParser;
import edu.cmu.cs.lti.learning.feature.extractor.SentenceFeatureExtractor;
import edu.cmu.cs.lti.learning.model.FeatureAlphabet;
import edu.cmu.cs.lti.learning.model.FeatureVector;
import edu.cmu.cs.lti.learning.model.RealValueHashFeatureVector;
import edu.cmu.cs.lti.learning.model.WekaModel;
import edu.cmu.cs.lti.script.type.EventMention;
import edu.cmu.cs.lti.script.type.StanfordCorenlpSentence;
import edu.cmu.cs.lti.script.type.StanfordCorenlpToken;
import edu.cmu.cs.lti.uima.annotator.AbstractConfigAnnotator;
import edu.cmu.cs.lti.uima.util.TokenAlignmentHelper;
import edu.cmu.cs.lti.uima.util.UimaNlpUtils;
import edu.cmu.cs.lti.utils.Configuration;
import gnu.trove.map.TObjectDoubleMap;
import gnu.trove.map.hash.TObjectDoubleHashMap;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import java.io.File;
import java.lang.reflect.InvocationTargetException;

/**
 * Created with IntelliJ IDEA.
 * Date: 9/14/15
 * Time: 3:00 PM
 *
 * @author Zhengzhong Liu
 */
public class RealisTypeAnnotator extends AbstractConfigAnnotator {
    public static final String PARAM_MODEL_DIRECTORY = "modelDirectory";

    public static final String PARAM_CONFIG = "configPath";

    @ConfigurationParameter(name = PARAM_MODEL_DIRECTORY)
    private File modelDirectory;

    private static SentenceFeatureExtractor extractor;

    private WekaModel model;

    private Table<Integer, Integer, FeatureVector> dummy;

    private TokenAlignmentHelper alignmentHelper = new TokenAlignmentHelper();

    private String goldTokenComponentId = TbfEventDataReader.class.getSimpleName();

    @Override
    public void initialize(UimaContext aContext) throws ResourceInitializationException {
        super.initialize(aContext);
        loadModel();
    }

    private void loadModel() throws ResourceInitializationException {
        logger.info("Loading models ...");
        try {
            model = new WekaModel(modelDirectory);
        } catch (Exception e) {
            throw new ResourceInitializationException(e);
        }

        String featureSpec = config.get("edu.cmu.cs.lti.features.realis.spec");

        FeatureSpecParser parser = new FeatureSpecParser(config.get("edu.cmu.cs.lti.feature.sentence.package.name"));
        Configuration realisSpec = parser.parseFeatureFunctionSpecs(featureSpec);

        // Currently no document level realis features.
        Configuration placeHolderSpec = new Configuration();

        try {
            extractor = new SentenceFeatureExtractor(model.getAlphabet(), config, realisSpec, placeHolderSpec, false);
        } catch (ClassNotFoundException | NoSuchMethodException | InvocationTargetException | IllegalAccessException
                | InstantiationException e) {
            e.printStackTrace();
        }

        dummy = HashBasedTable.create();
        logger.info("Model loaded");
        logger.info("Feature size is " + model.getAlphabet().getAlphabetSize());
    }

    @Override
    public void process(JCas aJCas) throws AnalysisEngineProcessException {
//        UimaConvenience.printProcessLog(aJCas);
        annotateViaModel(aJCas);
    }

    private void annotateViaModel(JCas aJCas) {
        extractor.initWorkspace(aJCas);
        alignmentHelper.loadWord2Stanford(aJCas, goldTokenComponentId);

        FeatureAlphabet alphabet = model.getAlphabet();

        for (StanfordCorenlpSentence sentence : JCasUtil.select(aJCas, StanfordCorenlpSentence.class)) {
            extractor.resetWorkspace(aJCas, sentence);

            for (EventMention mention : JCasUtil.selectCovered(EventMention.class, sentence)) {
                TObjectDoubleMap<String> rawFeatures = new TObjectDoubleHashMap<>();

                FeatureVector mentionFeatures = new RealValueHashFeatureVector(alphabet);
                StanfordCorenlpToken headWord = UimaNlpUtils.findHeadFromStanfordAnnotation(mention);

                int head = extractor.getElementIndex(headWord);

                if (head < 0) {
                    logger.warn(String.format("Cannot find headword for mention [%s].", mention.getCoveredText()));
                }

                // If the head is null, this will quietly produce the first label, which is bad.
                extractor.extract(head, mentionFeatures, dummy);

                for (FeatureVector.FeatureIterator iter = mentionFeatures.featureIterator(); iter.hasNext(); ) {
                    iter.next();
                    rawFeatures.put(alphabet.getFeatureNames(iter.featureIndex())[0], iter.featureValue());
                }

                // Do prediction.
                try {
                    Pair<Double, String> prediction = model.classify(rawFeatures);
                    mention.setRealisType(prediction.getRight());
                    mention.setRealisConfidence(prediction.getLeft());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
