package edu.cmu.cs.lti.emd.learn.feature.functions;

import com.google.common.collect.ArrayListMultimap;
import edu.cmu.cs.lti.emd.learn.feature.FeatureUtils;
import edu.cmu.cs.lti.script.type.SemaforAnnotationSet;
import edu.cmu.cs.lti.script.type.SemaforLabel;
import edu.cmu.cs.lti.script.type.SemaforLayer;
import edu.cmu.cs.lti.script.type.StanfordCorenlpToken;
import edu.cmu.cs.lti.uima.util.UimaConvenience;
import edu.cmu.cs.lti.uima.util.UimaNlpUtils;
import edu.cmu.cs.lti.utils.Configuration;
import gnu.trove.map.TObjectDoubleMap;
import org.apache.uima.fit.util.FSCollectionFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.javatuples.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created with IntelliJ IDEA.
 * Date: 9/6/15
 * Time: 8:48 PM
 *
 * @author Zhengzhong Liu
 */
public class FrameFeatures extends SequenceFeatureWithFocus {
    ArrayListMultimap<StanfordCorenlpToken, Pair<String, String>> triggerToArgs;
    Map<StanfordCorenlpToken, String> triggerToFrameName;

    public FrameFeatures(Configuration config) {
        super(config);
    }

    @Override
    public void initDocumentWorkspace(JCas context) {
    }

    @Override
    public void resetWorkspace(JCas aJCas, int begin, int end) {
        readFrames(aJCas, begin, end);
    }

    @Override
    public void extract(List<StanfordCorenlpToken> sequence, int focus, TObjectDoubleMap<String> features,
                        TObjectDoubleMap<String> featuresNeedForState) {
        if (focus > sequence.size() - 1 || focus < 0) {
            return;
        }
        StanfordCorenlpToken token = sequence.get(focus);
        if (triggerToArgs.containsKey(token)) {
            for (Pair<String, String> triggerAndType : triggerToArgs.get(token)) {
                features.put(FeatureUtils.formatFeatureName("FrameArgumentLemma", triggerAndType.getValue0()), 1);
                features.put(FeatureUtils.formatFeatureName("FrameArgumentRole", triggerAndType.getValue0()), 1);
            }
        }

        if (triggerToFrameName.containsKey(token)) {
            features.put(FeatureUtils.formatFeatureName("FrameName", triggerToFrameName.get(token)), 1);
        }
    }

    private void readFrames(JCas jCas, int begin, int end) {
        triggerToArgs = ArrayListMultimap.create();
        triggerToFrameName = new HashMap<>();
        for (SemaforAnnotationSet annoSet : JCasUtil.selectCovered(jCas, SemaforAnnotationSet.class, begin, end)) {
            String frameName = annoSet.getFrameName();

            SemaforLabel trigger = null;
            List<SemaforLabel> frameElements = new ArrayList<>();

            for (SemaforLayer layer : FSCollectionFactory.create(annoSet.getLayers(), SemaforLayer.class)) {
                String layerName = layer.getName();
                if (layerName.equals("Target")) {// Target that invoke the frame
                    trigger = layer.getLabels(0);
                } else if (layerName.equals("FE")) {// Frame element
                    FSArray elements = layer.getLabels();
                    if (elements != null) {
                        frameElements.addAll(FSCollectionFactory.create(elements, SemaforLabel.class).stream()
                                .collect(Collectors.toList()));
                    }
                }
            }

            StanfordCorenlpToken triggerHead = UimaNlpUtils.findHeadFromTreeAnnotation(trigger);
            if (triggerHead == null) {
                triggerHead = UimaConvenience.selectCoveredFirst(trigger, StanfordCorenlpToken.class);
            }
            if (triggerHead != null) {
                triggerToFrameName.put(triggerHead, frameName);
            }

            for (SemaforLabel label : frameElements) {
                StanfordCorenlpToken elementHead = UimaNlpUtils.findHeadFromTreeAnnotation(label);
                if (elementHead == null) {
                    elementHead = UimaConvenience.selectCoveredFirst(label, StanfordCorenlpToken.class);
                }
                if (elementHead != null) {
                    triggerToArgs.put(elementHead, Pair.with(elementHead.getLemma(), label.getName()));
                }
            }
        }
    }
}