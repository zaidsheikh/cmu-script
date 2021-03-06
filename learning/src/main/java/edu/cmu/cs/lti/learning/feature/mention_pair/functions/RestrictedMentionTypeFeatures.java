package edu.cmu.cs.lti.learning.feature.mention_pair.functions;

import com.google.common.base.Joiner;
import edu.cmu.cs.lti.learning.feature.sequence.FeatureUtils;
import edu.cmu.cs.lti.learning.model.MentionCandidate;
import edu.cmu.cs.lti.learning.model.NodeKey;
import edu.cmu.cs.lti.utils.Configuration;
import gnu.trove.map.TObjectDoubleMap;
import org.apache.uima.jcas.JCas;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * Date: 9/29/15
 * Time: 2:54 PM
 *
 * @author Zhengzhong Liu
 */
public class RestrictedMentionTypeFeatures extends AbstractMentionPairFeatures {
    public RestrictedMentionTypeFeatures(Configuration generalConfig, Configuration featureConfig) {
        super(generalConfig, featureConfig);
    }

    @Override
    public void initDocumentWorkspace(JCas context) {

    }

    @Override
    public void extract(JCas documentContext, TObjectDoubleMap<String> featuresNoLabel, List<MentionCandidate>
            candidates, int firstCandidateId, int secondCandidateId) {

    }

    @Override
    public void extractNodeRelated(JCas documentContext, TObjectDoubleMap<String> featuresNeedLabel,
                                   List<MentionCandidate> candidates, NodeKey firstNodeKey,
                                   NodeKey secondNodeKey) {
        String firstType = firstNodeKey.getMentionType();
        String secondType = secondNodeKey.getMentionType();
        String[] types = {firstType, secondType};
        addBoolean(featuresNeedLabel, FeatureUtils.formatFeatureName("MentionTypePair", Joiner.on(":").join(types)));

        if (firstType.equals(secondType)){
            addBoolean(featuresNeedLabel, "MentionTypeMatch");
        }

    }

    @Override
    public void extract(JCas documentContext, TObjectDoubleMap<String> featuresNoLabel, MentionCandidate
            candidate) {
        addBoolean(featuresNoLabel, FeatureUtils.formatFeatureName("SingleType",
                candidate.getMentionType()));
    }

    @Override
    public void extractNodeRelated(JCas documentContext, TObjectDoubleMap<String> featureNeedLabel,
                                   MentionCandidate secondCandidate, NodeKey secondNodeKey) {

    }
}
