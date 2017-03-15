package edu.cmu.cs.lti.learning.runners;

import edu.cmu.cs.lti.after.annotators.LatentTreeAfterAnnotator;
import edu.cmu.cs.lti.after.train.LatentTreeAfterTrainer;
import edu.cmu.cs.lti.emd.pipeline.TrainingLooper;
import edu.cmu.cs.lti.event_coref.annotators.train.BeamJointTrainer;
import edu.cmu.cs.lti.learning.utils.ModelUtils;
import edu.cmu.cs.lti.pipeline.BasicPipeline;
import edu.cmu.cs.lti.pipeline.ProcessorWrapper;
import edu.cmu.cs.lti.uima.io.reader.CustomCollectionReaderFactory;
import edu.cmu.cs.lti.utils.Configuration;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.collection.metadata.CpeDescriptorException;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * Date: 12/12/16
 * Time: 3:26 PM
 *
 * @author Zhengzhong Liu
 */
public class PlainAfterModelRunner extends AbstractMentionModelRunner {
    public PlainAfterModelRunner(Configuration mainConfig, TypeSystemDescription typeSystemDescription) {
        super(mainConfig, typeSystemDescription);
    }

    public String trainAfterModel(Configuration config, CollectionReaderDescription trainReader,
                                  CollectionReaderDescription testReader, String processOutputDir, String suffix,
                                  File testGold, boolean skipTrain, boolean skipTest)
            throws UIMAException, IOException, ClassNotFoundException, NoSuchMethodException, InstantiationException,
            IllegalAccessException, InvocationTargetException, CpeDescriptorException, InterruptedException,
            SAXException {
        logger.info("Start after model training.");
        String cvModelDir = ModelUtils.getTrainModelPath(eventModelDir, config, suffix);

        int jointMaxIter = config.getInt("edu.cmu.cs.lti.perceptron.maxiter", 20);
        int modelOutputFreq = config.getInt("edu.cmu.cs.lti.perceptron.model.save.frequency", 3);

        boolean modelExists = new File(cvModelDir).exists();

        if (skipTrain && modelExists) {
            logger.info("Skipping after training, taking existing models.");
            logger.info("Directly run the test and evaluate the performance.");
            testAfter(config, testReader, cvModelDir, suffix, "test_only", processOutputDir,
                    testGold, skipTest);
        } else {
            logger.info("Saving model directory at : " + cvModelDir);
            AnalysisEngineDescription trainEngine = AnalysisEngineFactory.createEngineDescription(
                    LatentTreeAfterTrainer.class, typeSystemDescription,
                    LatentTreeAfterTrainer.PARAM_CONFIG_PATH, config.getConfigFile()
            );

            TrainingLooper trainer = new TrainingLooper(cvModelDir, trainReader, trainEngine, jointMaxIter,
                    modelOutputFreq) {
                @Override
                protected boolean loopActions() {
                    boolean modelSaved = super.loopActions();
                    BeamJointTrainer.loopAction();

                    if (modelSaved) {
                        String modelPath = cvModelDir + "_iter" + numIteration;
                        test(modelPath, "after_heldout_iter" + numIteration);
                    }
                    return modelSaved;
                }

                @Override
                protected void finish() throws IOException {
                    BeamJointTrainer.finish();
                    // Test using the final model.
                    String runName = "after_heldout_final";
                    test(cvModelDir, runName);
                }

                private void test(String model, String runName) {
                    if (testReader != null) {
                        try {
                            testAfter(config, testReader, model, suffix, runName, processOutputDir,
                                    testGold, skipTest);
                        } catch (SAXException | InterruptedException | IOException | CpeDescriptorException |
                                UIMAException e) {
                            e.printStackTrace();
                        }
                    }
                }

                @Override
                protected void saveModel(File modelOutputDir) throws IOException {
                    File modelOut = LatentTreeAfterTrainer.saveModels(modelOutputDir);
                    logger.info(String.format("Model is saved at : %s", modelOut));
                }
            };

            trainer.runLoopPipeline();

            logger.info("Tree Based After training finished ...");
        }
        return cvModelDir;
    }

    public void processEvalOutput(){

    }

    /**
     * Test the token based mention model and return the result directory as a reader
     */
    public CollectionReaderDescription testAfter(Configuration taskConfig,
                                                 CollectionReaderDescription reader, String afterModel,
                                                 String sliceSuffix, String runName, String outputDir,
                                                 File gold, boolean skipTest)
            throws SAXException, UIMAException, CpeDescriptorException, IOException, InterruptedException {
        String subEvalDir = sliceSuffix.equals(fullRunSuffix) ? "final" : "cv";

        return new ModelTester(mainConfig, "plain_after_model") {
            @Override
            CollectionReaderDescription runModel(Configuration taskConfig, CollectionReaderDescription reader, String
                    mainDir, String baseDir) throws SAXException, UIMAException,
                    CpeDescriptorException, IOException {
                return afterLinking(taskConfig, reader, afterModel, trainingWorkingDir, baseDir, skipTest);
            }
        }.run(taskConfig, reader, typeSystemDescription, sliceSuffix, runName, outputDir, subEvalDir, gold);
    }

    public CollectionReaderDescription afterLinking(Configuration taskConfig, CollectionReaderDescription reader,
                                                    String model, String mainDir, String baseOutput, boolean skipTest)
            throws UIMAException, SAXException, CpeDescriptorException, IOException {
        File outputFile = new File(mainDir, baseOutput);

        if (skipTest && outputFile.exists()) {
            logger.info("Skipping sent level tagging because output exists.");
            return CustomCollectionReaderFactory.createXmiReader(mainDir, baseOutput);
        } else {
            return new BasicPipeline(new ProcessorWrapper() {
                @Override
                public CollectionReaderDescription getCollectionReader() throws ResourceInitializationException {
                    return reader;
                }

                @Override
                public AnalysisEngineDescription[] getProcessors() throws ResourceInitializationException {
                    AnalysisEngineDescription afterLinker = AnalysisEngineFactory.createEngineDescription(
                            LatentTreeAfterAnnotator.class, typeSystemDescription,
                            LatentTreeAfterAnnotator.PARAM_MODEL_DIRECTORY, model,
                            LatentTreeAfterAnnotator.PARAM_CONFIG, taskConfig.getConfigFile().getPath()
                    );

                    List<AnalysisEngineDescription> annotators = new ArrayList<>();
                    RunnerUtils.addCorefPreprocessors(annotators, language);
                    annotators.add(afterLinker);
                    return annotators.toArray(new AnalysisEngineDescription[annotators.size()]);
                }
            }, mainDir, baseOutput).runWithOutput();
        }
    }

}