package edu.cmu.cs.lti.event_coref.decoding;

import com.google.common.collect.Table;
import edu.cmu.cs.lti.learning.feature.mention_pair.extractor.PairFeatureExtractor;
import edu.cmu.cs.lti.learning.model.*;
import edu.cmu.cs.lti.learning.model.decoding.JointLabelLinkAgenda;
import edu.cmu.cs.lti.learning.model.decoding.LabelLinkAgenda;
import edu.cmu.cs.lti.learning.model.decoding.NodeLinkingState;
import edu.cmu.cs.lti.learning.model.decoding.StateDelta;
import edu.cmu.cs.lti.learning.model.graph.EdgeType;
import edu.cmu.cs.lti.learning.model.graph.LabelledMentionGraphEdge;
import edu.cmu.cs.lti.learning.model.graph.MentionGraph;
import edu.cmu.cs.lti.learning.update.DiscriminativeUpdater;
import edu.cmu.cs.lti.utils.CollectionUtils;
import gnu.trove.map.TObjectDoubleMap;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.uima.jcas.JCas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static edu.cmu.cs.lti.learning.model.ModelConstants.COREF_MODEL_NAME;

/**
 * Created with IntelliJ IDEA.
 * Date: 2/28/16
 * Time: 4:22 PM
 *
 * @author Zhengzhong Liu
 */
public class BeamLatentTreeDecoder {
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private final GraphWeightVector corefWeights;
    private final PairFeatureExtractor mentionPairExtractor;

    private DiscriminativeUpdater updater;
    private final boolean isTraining;

    private TrainingStats corefTrainingStats;

    private final int beamSize;

    private final GraphFeatureVector dummyMentionFv;

    private final boolean useLaSO;

    private final boolean delayUpdate;

    /**
     * Training constructor
     *
     * @param corefWeights          Weight vector for coreference.
     * @param interMentionExtractor Extractor for inter event features.
     * @param updater               Updater that controls the update.
     * @param useLaSo               Whether to use Learning with Search Optimization (LaSO)
     * @param delayUpdate           Whether to do delayed LaSO update.  @throws ClassNotFoundException
     * @param beamSize              The beam size to search.   @throws NoSuchMethodException
     * @throws InstantiationException
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     */
    public BeamLatentTreeDecoder(GraphWeightVector corefWeights, PairFeatureExtractor interMentionExtractor,
                                 DiscriminativeUpdater updater, boolean useLaSo, boolean delayUpdate, int beamSize)
            throws ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException,
            InvocationTargetException {
        this(corefWeights, interMentionExtractor, useLaSo, delayUpdate, beamSize, true);
        this.updater = updater;
        corefTrainingStats = delayUpdate ? new TrainingStats(5, "coref") : new TrainingStats(250, "coref");

        logger.info("Starting the Beam Decoder for coreference training.");
    }

    /**
     * Testing constructor
     *
     * @param corefWeights         The weights.
     * @param mentionPairExtractor The coreference feature extractor.
     * @param beamSize             Beam size used for searching.
     * @throws ClassNotFoundException
     * @throws NoSuchMethodException
     * @throws InstantiationException
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     */
    public BeamLatentTreeDecoder(GraphWeightVector corefWeights, PairFeatureExtractor mentionPairExtractor,
                                 int beamSize)
            throws ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException,
            InvocationTargetException {
        this(corefWeights, mentionPairExtractor, false, false, beamSize, false);
        logger.info("Starting the Beam Decoder for coreference testing.");
    }

    private BeamLatentTreeDecoder(GraphWeightVector corefWeights, PairFeatureExtractor mentionPairExtractor,
                                  boolean useLaSo, boolean delayUpdate, int beamSize, boolean isTraining)
            throws ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException,
            InvocationTargetException {

        this.corefWeights = corefWeights;
        this.mentionPairExtractor = mentionPairExtractor;

        this.isTraining = isTraining;
        this.delayUpdate = delayUpdate;
        this.beamSize = beamSize;
        this.useLaSO = useLaSo;

        // The alphabet here are not so correct.
        dummyMentionFv = new GraphFeatureVector(corefWeights.getClassAlphabet(), corefWeights.getFeatureAlphabet());
    }

    /**
     * Decode method.
     *
     * @param aJCas        The JCas container.
     * @param mentionGraph The mention graph.
     * @param candidates   Candidates containing gold standard annotation.
     * @return The final decoding state.
     */
    public NodeLinkingState decode(JCas aJCas, MentionGraph mentionGraph, List<MentionCandidate> candidates) {
        // Prepare a gold agenda and a decoding agenda.
        JointLabelLinkAgenda goldAgenda = new JointLabelLinkAgenda(beamSize, candidates, mentionGraph);
        JointLabelLinkAgenda decodingAgenda = new JointLabelLinkAgenda(beamSize, candidates, mentionGraph);

        mentionPairExtractor.initWorkspace(aJCas);

        for (int candidateIndex = 0; candidateIndex < candidates.size(); candidateIndex++) {
            decodingAgenda.prepareExpand();
            if (isTraining) {
                goldAgenda.prepareExpand();
                expandGoldLink(mentionGraph, candidates, candidateIndex, goldAgenda);
            }

            expandState(mentionGraph, candidates, candidateIndex, decodingAgenda);

//            logger.info("Update states for decoding.");
            decodingAgenda.updateStates();

//            logger.info("Showing current agenda");
//            logger.info(decodingAgenda.toString());

            if (isTraining) {
                // Expand for gold.
//                logger.info("Update states for gold.");
                goldAgenda.updateStates();

                if (useLaSO) {
                    // Record updates;
                    updater.recordLaSOUpdate(decodingAgenda, goldAgenda);
                    if (!delayUpdate) {
                        TObjectDoubleMap<String> losses = updater.update();
                        corefTrainingStats.addLoss(logger, losses.get(COREF_MODEL_NAME));
//                        logger.info("Loss is " + losses.get(COREF_MODEL_NAME));
                    }
                }

            }
//            DebugUtils.pause();
        }

        if (isTraining) {
//            logger.info("List of decodings in order");
//            for (NodeLinkingState nodeLinkingState : decodingAgenda.getOrderedStates()) {
//                logger.info(nodeLinkingState.toString());
//            }
//
//            logger.info("Best Gold.");
//            logger.info(goldAgenda.getBestBeamState().getDecodingTree().toString());
//
//            logger.info("Best Decoding.");
//            logger.info(decodingAgenda.getBestBeamState().getDecodingTree().toString());

            // The final check matches the first item in the agendas, while the searching check only ensure containment.
            updater.recordFinalUpdate(decodingAgenda, goldAgenda);
            // Update based on cumulative errors.
            TObjectDoubleMap<String> losses = updater.update();
            corefTrainingStats.addLoss(logger, losses.get(COREF_MODEL_NAME) / mentionGraph.numNodes());

            logger.debug("Loss is " + losses.get(COREF_MODEL_NAME) / mentionGraph.numNodes());
        }

        return decodingAgenda.getBestBeamState();
    }

    private void expandState(MentionGraph mentionGraph, List<MentionCandidate> candidates, int candidateIndex,
                             LabelLinkAgenda agenda) {
        MentionCandidate candidate = candidates.get(candidateIndex);
        MentionKey currNode = candidate.asKey();

        for (NodeLinkingState nodeLinkingState : agenda) {
            List<List<Pair<Pair<NodeKey, LabelledMentionGraphEdge>, Pair<EdgeType, Double>>>> allLinks = new
                    ArrayList<>();

            for (NodeKey currNodeKey : currNode) {
                // First store all the possible links that can be formed from this node.
                List<Pair<Pair<NodeKey, LabelledMentionGraphEdge>, Pair<EdgeType, Double>>> currLinks = new
                        ArrayList<>();

                for (int ant = 0; ant < MentionGraph.getNodeIndex(candidateIndex); ant++) {
                    MentionKey antNodeKeys = nodeLinkingState.getNode(ant);

                    for (NodeKey antNodeKey : antNodeKeys) {
                        LabelledMentionGraphEdge edge = mentionGraph
                                .getLabelledEdge(candidates, antNodeKey, currNodeKey);

                        for (Map.Entry<EdgeType, Double> labelScore : edge.getAllLabelScore(corefWeights).entrySet()) {

                            double linkScore = labelScore.getValue();
                            EdgeType edgeType = labelScore.getKey();
                            currLinks.add(Pair.of(Pair.of(currNodeKey, edge), Pair.of(edgeType, linkScore)));
                        }
                    }
                }
                allLinks.add(currLinks);
            }

            // So now what if each node choose a link? we have their cartesian number of possible linking state.
            List<List<Pair<Pair<NodeKey, LabelledMentionGraphEdge>, Pair<EdgeType, Double>>>> possibleLinkComb =
                    CollectionUtils.cartesian(allLinks);

            for (List<Pair<Pair<NodeKey, LabelledMentionGraphEdge>, Pair<EdgeType, Double>>> links : possibleLinkComb) {
                StateDelta decision = new StateDelta(nodeLinkingState);
                decision.addNode(currNode, dummyMentionFv, 0);
                for (Pair<Pair<NodeKey, LabelledMentionGraphEdge>, Pair<EdgeType, Double>> link : links) {
                    NodeKey dep = link.getLeft().getLeft();
                    LabelledMentionGraphEdge edge = link.getLeft().getRight();
                    NodeKey gov = edge.getGovKey();
                    Pair<EdgeType, Double> labelScore = link.getRight();

                    decision.addLink(labelScore.getKey(), gov, dep, labelScore.getValue(), edge.getFeatureVector());
                }
                agenda.expand(decision);
            }
        }
    }

    private void expandGoldLink(MentionGraph mentionGraph, List<MentionCandidate> candidates, int candidateIndex,
                                JointLabelLinkAgenda agenda) {

        MentionCandidate candidate = candidates.get(candidateIndex);
        MentionKey currNode = candidate.asKey();

        int currentNodeIndex = MentionGraph.getNodeIndex(candidateIndex);

        // Expand each possible beam state.
        for (NodeLinkingState nodeLinkingState : agenda) {

            // Each list store the possible edge for one current key.
            // We need to get the combination of these edges to create all possible linking.
            List<List<Pair<NodeKey, LabelledMentionGraphEdge>>> allLinks = new ArrayList<>();

            // Decide the link for each node key.
            for (NodeKey currNodeKey : currNode) {
                int numPossibleCorrectLinks = 0;

                List<Pair<NodeKey, LabelledMentionGraphEdge>> currLinks = new ArrayList<>();

                for (int ant = 0; ant < MentionGraph.getNodeIndex(candidateIndex); ant++) {
                    Table<NodeKey, NodeKey, LabelledMentionGraphEdge> realGraphEdges = mentionGraph
                            .getEdge(ant, currentNodeIndex).getRealLabelledEdges();

                    Map<NodeKey, LabelledMentionGraphEdge> correctAntEdges = realGraphEdges.column(currNodeKey);
                    numPossibleCorrectLinks += correctAntEdges.size();

                    for (Map.Entry<NodeKey, LabelledMentionGraphEdge> correctAntEdge : correctAntEdges.entrySet()) {
                        NodeKey correctAnt = correctAntEdge.getKey();
                        LabelledMentionGraphEdge realGraphEdge = realGraphEdges.get(correctAnt, currNodeKey);

                        currLinks.add(Pair.of(currNodeKey, realGraphEdge));
                    }
                }

                allLinks.add(currLinks);


                if (numPossibleCorrectLinks == 0) {
                    throw new IllegalStateException("No possible links for for " + currNodeKey);
                }
            }

            List<List<Pair<NodeKey, LabelledMentionGraphEdge>>> possibleLinkCartesian = CollectionUtils.cartesian
                    (allLinks);

            for (List<Pair<NodeKey, LabelledMentionGraphEdge>> links : possibleLinkCartesian) {
                StateDelta decision = new StateDelta(nodeLinkingState);
                decision.addNode(currNode, dummyMentionFv, 0);
                for (Pair<NodeKey, LabelledMentionGraphEdge> link : links) {
                    NodeKey dep = link.getLeft();
                    LabelledMentionGraphEdge edge = link.getRight();
                    NodeKey gov = edge.getGovKey();


                    if (edge.hasActualEdgeType()) {
                        double correctLabelScore = edge.getCorrectLabelScore(corefWeights);
                        decision.addLink(edge.getActualEdgeType(), gov, dep, correctLabelScore, edge
                                .getFeatureVector());
                    }
                }
                agenda.expand(decision);
            }
        }
    }

}
