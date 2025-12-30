package fr.uga.pddl4j.examples.asp;
import fr.uga.pddl4j.parser.DefaultParsedProblem;
import fr.uga.pddl4j.plan.Plan;
import fr.uga.pddl4j.planners.AbstractPlanner;
import fr.uga.pddl4j.problem.DefaultProblem;
import fr.uga.pddl4j.problem.State;
import fr.uga.pddl4j.problem.Problem;
import fr.uga.pddl4j.problem.operator.Action;
import fr.uga.pddl4j.problem.Goal;
import fr.uga.pddl4j.plan.SequentialPlan;
import fr.uga.pddl4j.heuristics.state.StateHeuristic;


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

    // RNG pour les random walks
    private final Random rng = new Random(0);
    private int walkLength = 20;        // LENGTH_WALK
    private int numWalks = 200;         // NUM_WALK
    private int maxStepsNoImprove = 50; // MAX_STEPS (counter)
    private StateHeuristic.Name heuristicName = StateHeuristic.Name.FAST_FORWARD;

    private StateHeuristic heuristic;
    @CommandLine.Option(names = {"--walkLength", "-L"}, defaultValue = "20",
            paramLabel = "<int>",
            description = "Longueur maximale d'une random walk (LENGTH_WALK).")
    public void setWalkLength(final int L) {
        if (L <= 0) throw new IllegalArgumentException("walkLength must be > 0");
        this.walkLength = L;
    }

    @CommandLine.Option(names = {"--numWalks", "-N"}, defaultValue = "200",
            paramLabel = "<int>",
            description = "Nombre de random walks par étape (NUM_WALK).")
    public void setNumWalks(final int n) {
        if (n <= 0) throw new IllegalArgumentException("numWalks must be > 0");
        this.numWalks = n;
    }

    @CommandLine.Option(names = {"--maxNoImprove", "-C"}, defaultValue = "50",
            paramLabel = "<int>",
            description = "Nombre max d'itérations sans amélioration avant restart (counter).")
    public void setMaxStepsNoImprove(final int c) {
        if (c < 0) throw new IllegalArgumentException("maxNoImprove must be >= 0");
        this.maxStepsNoImprove = c;
    }

    @CommandLine.Option(names = {"--heuristic", "-H"}, defaultValue = "FAST_FORWARD",
            paramLabel = "<name>",
            description = "Heuristique: FAST_FORWARD, SUM, MAX, SET_LEVEL, ... (selon PDDL4J).")
    public void setHeuristicName(final StateHeuristic.Name h) {
        this.heuristicName = h;
    }


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
     * Random walk: on prend ensuite une action au hasard.
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

    @Override
    public boolean isSupported(Problem problem) {
        return true;
    }

    private boolean isGoal(final Problem problem, final State s) {
        final DefaultProblem pb = (DefaultProblem) problem;
        return s.satisfy(pb.getGoal());
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

    private int h(final Problem problem, final State s) {
        final DefaultProblem pb = (DefaultProblem) problem;
        return this.heuristic.estimate(s, pb.getGoal());
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




    /**
     * Pure Random Walks
     */
    private WalkResult pureRandomWalk(final Problem problem,
                                      final State start,
                                      final List<Action> allActions) {

        WalkResult best = null;
        int bestH = Integer.MAX_VALUE;

        for (int i = 0; i < this.numWalks; i++) {
            WalkResult wr = randomWalkRollout(problem, start, allActions, this.walkLength);

            if (wr.reachedGoal) {
                return wr;
            }

            if (!wr.deadEnd) {
                int hv = h(problem,wr.endState); // endpoint evaluation uniquement
                if (hv < bestH) {
                    bestH = hv;
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
        final DefaultProblem pb = (DefaultProblem) problem;
        final List<Action> actions = pb.getActions();

        // Init heuristic
        this.heuristic = StateHeuristic.getInstance(this.heuristicName, pb);

        final long startTime = System.currentTimeMillis();
        final long timeoutMs = this.getTimeout();

        // Algorithm 1 variables
        State s = new State(pb.getInitialState());
        SequentialPlan plan = new SequentialPlan();
        int t = 0;

        int hmin = h(problem,s);
        int counter = 0;

        LOGGER.info("\n========== RWPlanner ==========\n");
        LOGGER.info("walkLength={} numWalks={} maxStepsNoImprove={} heuristic={}\n",
                this.walkLength, this.numWalks, this.maxStepsNoImprove, this.heuristicName);

        LOGGER.info("\nTimeout(ms)={} \n actions={} \n walkLength={} \n numWalks={} \n maxStepsNoImprove={}\n",
                timeoutMs, actions.size(), this.walkLength, this.numWalks, this.maxStepsNoImprove);

        while (!isGoal(pb, s)) {

            // timeout check
            if (timeoutMs > 0 && System.currentTimeMillis() - startTime > timeoutMs) {
                LOGGER.info("Timeout reached -> returning null");
                return null;
            }

            // restart condition
            if (counter > this.maxStepsNoImprove) {
                LOGGER.info("Restart (counter>{})", this.maxStepsNoImprove);
                s = new State(pb.getInitialState());
                plan = new SequentialPlan();
                t=0;
                hmin = h(problem,s);
                counter = 0;
            }

            // Algorithm 2
            WalkResult wr = pureRandomWalk(pb, s, actions);

            // dead-end / empty -> restart
            if (wr.deadEnd || wr.actions.isEmpty()) {
                LOGGER.info("Dead-end or empty walk -> restart");
                s = new State(pb.getInitialState());
                plan = new SequentialPlan();
                t = 0;
                hmin = h(problem,s);
                counter = 0;
                continue;
            }

            // Ajouter actions dans le plan
            for (Action a : wr.actions) {
                plan.add(t,a);
                t++;
            }

            // Deplacer a l'etet suivant
            s = wr.endState;

            // Si on arrive dans le goal
            if (wr.reachedGoal || isGoal(pb, s)) {
                LOGGER.info("Goal reached! plan length={}", plan.size());
                this.getStatistics().setTimeToSearch(System.currentTimeMillis() - startTime);
                return plan;
            }

            // Mise a jour
            int hs = h(pb,s);
            if (hs < hmin) {
                hmin = hs;
                counter = 0;
            } else {
                counter++;
            }
        }

        LOGGER.info("Goal already satisfied at start -> empty plan");
        this.getStatistics().setTimeToSearch(System.currentTimeMillis() - startTime);
        return plan;
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
