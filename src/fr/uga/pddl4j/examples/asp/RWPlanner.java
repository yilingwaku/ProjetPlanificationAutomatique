package fr.uga.pddl4j.examples.asp;
import fr.uga.pddl4j.parser.DefaultParsedProblem;
import fr.uga.pddl4j.plan.Plan;
import fr.uga.pddl4j.planners.AbstractPlanner;
import fr.uga.pddl4j.problem.DefaultProblem;
import fr.uga.pddl4j.problem.State;
import fr.uga.pddl4j.problem.Problem;
import fr.uga.pddl4j.problem.operator.Action;
import fr.uga.pddl4j.problem.Goal;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine;


import java.util.List;
import java.util.ArrayList;
import java.util.Random;


/**
 * RWPlanner: Simple planner based on random walks.
 */
@CommandLine.Command(
        name = "RWPlanner",
        version = "RWPlanner 0.1",
        description = "Skeleton planner (parse + instantiate only). Random-walk search will be added step by step.",
        sortOptions = false,
        mixinStandardHelpOptions = true,
        headerHeading = "Usage:%n",
        synopsisHeading = "%n",
        descriptionHeading = "%nDescription:%n%n",
        parameterListHeading = "%nParameters:%n",
        optionListHeading = "%nOptions:%n"
)
public class RWPlanner extends AbstractPlanner {

    private static final Logger LOGGER = LogManager.getLogger(RWPlanner.class.getName());

    // RNG pour les random walks (seed fixe = reproductible)
    private final Random rng = new Random(0);

    /**
     * Résultat d'une seule random walk (rollout).
     */
    private static class WalkResult {
        final State endState;
        final List<Action> actions;
        final boolean deadEnd;
        final boolean reachedGoal;

        WalkResult(State endState, List<Action> actions, boolean deadEnd, boolean reachedGoal) {
            this.endState = endState;
            this.actions = actions;
            this.deadEnd = deadEnd;
            this.reachedGoal = reachedGoal;
        }
    }


    /**
     * Retourne la liste des actions dans un état donné.
     * Random walk: on prendra ensuite une action au hasard.
     */
    private List<Action> getApplicableActions(final State state, final List<Action> allActions) {
        final List<Action> applicable = new ArrayList<>();
        for (Action a : allActions) {
            if (state.satisfy(a.getPrecondition())) {
                applicable.add(a);
            }
        }
        return applicable;
    }


    /**
     * Une seule rollout de longueur maxLen
     * À chaque pas: A = actions applicables(s), choisir une action au hasard, appliquer.
     * Si A est vide alors dead-end et on s'arrête.
     */
    private WalkResult randomWalkRollout(final Problem problem,final State start, final List<Action> allActions, final int maxLen) {

        State current = new State(start);
        final List<Action> seq = new ArrayList<>();

        for (int j = 0; j < maxLen; j++) {
            final List<Action> applicable = getApplicableActions(current, allActions);

            // dead-end: aucune action applicable
            if (applicable.isEmpty()) {
                return new WalkResult(current, seq, true,false);
            }

            // choix d'une action applicable
            final int idx = rng.nextInt(applicable.size());
            final Action chosen = applicable.get(idx);
            seq.add(chosen);

            final State next = new State(current);
            // appliquer l'effet inconditionnel
            next.apply(chosen.getUnconditionalEffect());
            // appliquer les effets conditionnels (ADL)
            next.apply(chosen.getConditionalEffects());

            current = next;

            if(isGoal(problem,current)){
                return new WalkResult(current,seq,false,true);
            }

        }
        return new WalkResult(current, seq, false, false);
    }


    @Override
    public boolean isSupported(Problem problem) {
        return true;
    }

    /**
     * Instancier le problème de planification
     */
    @Override
    public Problem instantiate(DefaultParsedProblem problem) {
        final Problem pb = new DefaultProblem(problem);
        pb.instantiate();
        return pb;
    }

    private WalkResult pureRandomWalk(final Problem problem,
                                      final State start,
                                      final List<Action> allActions,
                                      final int maxLen,
                                      final int numWalks) {

        WalkResult best = null;

        for (int i = 0; i < numWalks; i++) {
            WalkResult wr = randomWalkRollout(problem, start, allActions, maxLen);

            if (wr.reachedGoal) {
                return wr; // succès immédiat
            }

            // fallback simple temporaire: prendre la walk la plus courte (on mettra l'heuristique ensuite)
            if (!wr.deadEnd) {
                if (best == null || wr.actions.size() < best.actions.size()) {
                    best = wr;
                }
            }
        }

        if (best == null) {
            return new WalkResult(new State(start), new ArrayList<>(), true, false);
        }
        return best;
    }

    /**
     * Solve method: Solution
     */
    @Override
    public Plan solve(final Problem problem) {
        // Vérifier qu'on voit bien l'état initial,but et actions
        final State s0 = new State(problem.getInitialState());
        final int maxLen = 20;
        final int numWalks = 50;

        final List<Action> actions = problem.getActions();

        LOGGER.info("========== RWPlanner ==========\n");
        LOGGER.info("Timeout (ms): {}\n", this.getTimeout());
        LOGGER.info("Number of ground actions: {}\n", actions.size());
        LOGGER.info("Initial state (s0): {}\n", s0);

        // WalkResult wr = randomWalkRollout(s0, actions, maxLen);
        WalkResult wr = pureRandomWalk(problem,s0,actions,maxLen,numWalks);

        LOGGER.info("One rollout done: len={} deadEnd={} endState={}\n",
                wr.actions.size(), wr.deadEnd, wr.endState);

        return null;
    }

    private boolean isGoal(final Problem problem, final State s) {
        final DefaultProblem pb = (DefaultProblem) problem;
        return s.satisfy(pb.getGoal());
    }



    public static void main(String[] args) {
        try {
            final RWPlanner planner = new RWPlanner();
            CommandLine cmd = new CommandLine(planner);
            cmd.execute(args);
        } catch (IllegalArgumentException e) {
            LOGGER.fatal(e.getMessage());
        }
    }
}
