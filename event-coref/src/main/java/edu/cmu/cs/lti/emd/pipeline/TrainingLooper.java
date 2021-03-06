package edu.cmu.cs.lti.emd.pipeline;

import edu.cmu.cs.lti.uima.pipeline.LoopPipeline;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.resource.ResourceInitializationException;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

/**
 * Created with IntelliJ IDEA.
 * Date: 8/23/15
 * Time: 3:05 PM
 *
 * @author Zhengzhong Liu
 */
public abstract class TrainingLooper extends LoopPipeline {
    private int maxIteration;
    protected int numIteration;
    private String modelBasename;
    private int numberIterToSave = 3;

    public TrainingLooper(String modelOutputBasename, CollectionReaderDescription reader, AnalysisEngineDescription
            trainer, int maxIter, int numIterToSave)
            throws ResourceInitializationException, ClassNotFoundException, NoSuchMethodException,
            InvocationTargetException, InstantiationException, IllegalAccessException, IOException {
        super(reader, trainer);
        this.maxIteration = maxIter;
        this.numIteration = 0;
        this.numberIterToSave = numIterToSave;
        this.modelBasename = modelOutputBasename;
        logger.info("Trainer started, maximum iteration is " + maxIteration);
    }

    @Override
    protected boolean checkStopCriteria() {
        return numIteration >= maxIteration;
    }

    @Override
    protected void stopActions() {
        logger.info("Finalizing the training ...");
        try {
            logger.info("Saving final models at " + modelBasename);
            saveModel(new File(modelBasename));
            finish();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected boolean loopActions() {
        numIteration++;
        boolean modelSaved = false;
        if (numIteration % numberIterToSave == 0) {
            try {
                logger.info("Saving models for iteration " + numIteration + " using the " + numberIterToSave + " loop.");
                saveModel(new File(modelBasename + "_iter" + numIteration));
                modelSaved = true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        logger.info(String.format("Iteration %d finished ...", numIteration));
        return modelSaved;
    }

    protected abstract void finish() throws IOException;

    protected abstract void saveModel(File modelOutputDir) throws IOException;
}
