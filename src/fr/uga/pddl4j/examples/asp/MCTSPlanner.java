package fr.uga.pddl4j.examples.asp;

import fr.uga.pddl4j.parser.DefaultParsedProblem;
import fr.uga.pddl4j.plan.Plan;
import fr.uga.pddl4j.plan.SequentialPlan;
import fr.uga.pddl4j.planners.AbstractPlanner;
import fr.uga.pddl4j.problem.DefaultProblem;
import fr.uga.pddl4j.problem.Problem;
import fr.uga.pddl4j.problem.State;
import fr.uga.pddl4j.problem.operator.Action;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * MCTSPlanner: Monte Carlo Tree Search planner
 * Selection: UCB1
 * Expansion: Ajouter une action applicable
 * Rollout: random walk
 * Backprop: visits++, wins += reward
 */
@CommandLine.Command(
        name = "MCTSPlanner",
        version = "MCTSPlanner 0.1",
        description = "Minimal MCTS planner (Selection UCB, Expansion, Random Rollout, Backprop).",
        sortOptions = false,
        mixinStandardHelpOptions = true,
        headerHeading = "Usage:%n",
        synopsisHeading = "%n",
        descriptionHeading = "%nDescription:%n%n",
        parameterListHeading = "%nParameters:%n",
        optionListHeading = "%nOptions:%n"
)
public class MCTSPlanner extends AbstractPlanner {

    private static final Logger LOGGER = LogManager.getLogger(MCTSPlanner.class.getName());

    // RNG seed
    private final Random rng = new Random(0);

    // MCTS params
    private int iterations = 300;       // nombre d'itérations MCTS
    private int rolloutDepth = 40;      // profondeur max des rollouts
    private int maxPlanLength = 500;    // sécurité pour éviter boucle infinie
    private double explorationC = 1.4;  // constante UCB

    @CommandLine.Option(names = {"--iterations", "-I"}, defaultValue = "300",
            paramLabel = "<int>",
            description = "Nombre d'itérations MCTS par étape (par décision).")
    public void setIterations(final int it) {
        if (it <= 0) throw new IllegalArgumentException("iterations must be > 0");
        this.iterations = it;
    }

    @CommandLine.Option(names = {"--rolloutDepth", "-R"}, defaultValue = "40",
            paramLabel = "<int>",
            description = "Profondeur max d'un rollout aléatoire.")
    public void setRolloutDepth(final int d) {
        if (d <= 0) throw new IllegalArgumentException("rolloutDepth must be > 0");
        this.rolloutDepth = d;
    }

    @CommandLine.Option(names = {"--maxPlanLength", "-P"}, defaultValue = "500",
            paramLabel = "<int>",
            description = "Longueur max du plan.")
    public void setMaxPlanLength(final int p) {
        if (p <= 0) throw new IllegalArgumentException("maxPlanLength must be > 0");
        this.maxPlanLength = p;
    }

    @CommandLine.Option(names = {"--exploration", "-C"}, defaultValue = "1.4",
            paramLabel = "<double>",
            description = "Constante d'exploration UCB (ex: 1.4).")
    public void setExplorationC(final double c) {
        if (c < 0.0) throw new IllegalArgumentException("exploration must be >= 0");
        this.explorationC = c;
    }

    // Structure pour rollouts

    private static class WalkResult {
        final State endState;
        final boolean deadEnd;
        final boolean reachedGoal;

        WalkResult(State endState, boolean deadEnd, boolean reachedGoal) {
            this.endState = endState;
            this.deadEnd = deadEnd;
            this.reachedGoal = reachedGoal;
        }
    }

    // MCTS Node

    private static class MCTSNode {
        final MCTSNode parent;
        final Action actionFromParent;

        final List<Action> untried;
        final List<MCTSNode> children;

        int visits;
        double wins;

        MCTSNode(MCTSNode parent, Action actionFromParent, List<Action> untried) {
            this.parent = parent;
            this.actionFromParent = actionFromParent;
            this.untried = untried;
            this.children = new ArrayList<>();
            this.visits = 0;
            this.wins = 0.0;
        }

        boolean isFullyExpanded() {
            return untried.isEmpty();
        }

        boolean hasChildren() {
            return !children.isEmpty();
        }
    }


    @Override
    public boolean isSupported(Problem problem) {
        return true;
    }

    @Override
    public Problem instantiate(DefaultParsedProblem problem) {
        final Problem pb = new DefaultProblem(problem);
        pb.instantiate();
        return pb;
    }

    private boolean isGoal(final Problem problem, final State s) {
        final DefaultProblem pb = (DefaultProblem) problem;
        return s.satisfy(pb.getGoal());
    }

    private List<Action> getApplicableActions(final State state, final List<Action> allActions) {
        final List<Action> applicable = new ArrayList<>();
        for (Action a : allActions) {
            if (state.satisfy(a.getPrecondition())) {
                applicable.add(a);
            }
        }
        return applicable;
    }

    private State applyAction(final State s, final Action a) {
        final State next = new State(s);
        next.apply(a.getUnconditionalEffect());
        next.apply(a.getConditionalEffects());
        return next;
    }

    /**
     * Random rollout commence a state, termine a maxLen.
     * reward depend de reachedGoal.
     */
    private WalkResult randomWalkRollout(final Problem problem,
                                         final State start,
                                         final List<Action> allActions,
                                         final int maxLen) {

        State current = new State(start);

        for (int j = 0; j < maxLen; j++) {
            if (isGoal(problem, current)) {
                return new WalkResult(current, false, true);
            }

            final List<Action> applicable = getApplicableActions(current, allActions);
            if (applicable.isEmpty()) {
                return new WalkResult(current, true, false);
            }

            final Action chosen = applicable.get(rng.nextInt(applicable.size()));
            current = applyAction(current, chosen);
        }

        return new WalkResult(current, false, isGoal(problem, current));
    }


    private MCTSNode bestChildUCB(final MCTSNode node, final double c) {
        MCTSNode best = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (MCTSNode child : node.children) {
            if (child.visits == 0) return child;

            final double exploitation = child.wins / child.visits;
            final double exploration = c * Math.sqrt(Math.log(Math.max(1, node.visits)) / child.visits);
            final double score = exploitation + exploration;

            if (score > bestScore) {
                bestScore = score;
                best = child;
            }
        }
        return best;
    }

    /**
     * Choisir un enfant suivant par l'etat courant de MCTS.
     */
    private Action mctsChooseAction(final Problem problem,
                                    final State rootState,
                                    final List<Action> allActions) {

        final List<Action> rootApplicable = getApplicableActions(rootState, allActions);
        if (rootApplicable.isEmpty()) return null;

        final MCTSNode root = new MCTSNode(null, null, new ArrayList<>(rootApplicable));

        for (int it = 0; it < this.iterations; it++) {

            State sim = new State(rootState);
            MCTSNode node = root;

            // Selection
            while (node.isFullyExpanded() && node.hasChildren()) {
                node = bestChildUCB(node, this.explorationC);
                sim = applyAction(sim, node.actionFromParent);
            }

            // Expansion
            if (!node.untried.isEmpty()) {
                final int idx = rng.nextInt(node.untried.size());
                final Action a = node.untried.remove(idx);

                sim = applyAction(sim, a);

                final List<Action> applicable = getApplicableActions(sim, allActions);
                final MCTSNode child = new MCTSNode(node, a, new ArrayList<>(applicable));
                node.children.add(child);
                node = child;
            }

            // Rollout
            final WalkResult wr = randomWalkRollout(problem, sim, allActions, this.rolloutDepth);
            final int reward = wr.reachedGoal ? 1 : 0;

            // Backpropagation
            while (node != null) {
                node.visits++;
                node.wins += reward;
                node = node.parent;
            }
        }

        // Action finale : child avec max visits
        MCTSNode best = null;
        int bestVisits = -1;
        for (MCTSNode child : root.children) {
            if (child.visits > bestVisits) {
                bestVisits = child.visits;
                best = child;
            }
        }

        return (best == null) ? null : best.actionFromParent;
    }

    // Solve

    @Override
    public Plan solve(final Problem problem) {
        final DefaultProblem pb = (DefaultProblem) problem;
        final List<Action> actions = pb.getActions();

        final long startTime = System.currentTimeMillis();
        final long timeoutMs = this.getTimeout();

        State s = new State(pb.getInitialState());
        SequentialPlan plan = new SequentialPlan();
        int t = 0;

        LOGGER.info("\n========== MCTSPlanner ==========\n");
        LOGGER.info("iterations={} rolloutDepth={} maxPlanLength={} explorationC={}\n",
                this.iterations, this.rolloutDepth, this.maxPlanLength, this.explorationC);
        LOGGER.info("Timeout(ms)={} actions={}\n", timeoutMs, actions.size());

        while (!isGoal(pb, s) && t < this.maxPlanLength) {

            // timeout
            if (timeoutMs > 0 && System.currentTimeMillis() - startTime > timeoutMs) {
                long runtime = System.currentTimeMillis() - startTime;
                LOGGER.info("Timeout reached -> returning null");
                System.out.println("RESULT: FAILURE");
                System.out.println("RESULT: PLAN_LENGTH=0");
                System.out.println("RESULT: RUNTIME_MS=" + runtime);
                return null;
            }

            final Action next = mctsChooseAction(pb, s, actions);
            if (next == null) {
                long runtime = System.currentTimeMillis() - startTime;
                LOGGER.info("No applicable action / MCTS couldn't choose -> failure");
                System.out.println("RESULT: FAILURE");
                System.out.println("RESULT: PLAN_LENGTH=0");
                System.out.println("RESULT: RUNTIME_MS=" + runtime);
                return null;
            }

            plan.add(t, next);
            t++;
            s = applyAction(s, next);
        }

        long runtime = System.currentTimeMillis() - startTime;

        if (isGoal(pb, s)) {
            LOGGER.info("Goal reached! plan length={}\n", t);
            this.getStatistics().setTimeToSearch(runtime);

            System.out.println("RESULT: SUCCESS");
            System.out.println("RESULT: PLAN_LENGTH=" + t);
            System.out.println("RESULT: RUNTIME_MS=" + runtime);
            return plan;
        } else {
            LOGGER.info("Max plan length reached without goal -> failure");
            System.out.println("RESULT: FAILURE");
            System.out.println("RESULT: PLAN_LENGTH=0");
            System.out.println("RESULT: RUNTIME_MS=" + runtime);
            return null;
        }
    }

    public static void main(String[] args) {
        try {
            final MCTSPlanner planner = new MCTSPlanner();
            CommandLine cmd = new CommandLine(planner);
            cmd.execute(args);
        } catch (IllegalArgumentException e) {
            LOGGER.fatal(e.getMessage());
        }
    }
}
