package fr.uga.pddl4j.examples.asp;
import fr.uga.pddl4j.parser.DefaultParsedProblem;
import fr.uga.pddl4j.plan.Plan;
import fr.uga.pddl4j.planners.AbstractPlanner;
import fr.uga.pddl4j.problem.DefaultProblem;
import fr.uga.pddl4j.problem.Problem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine;

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

    /**
     * Solve method: Solution
     */
    @Override
    public Plan solve(final Problem problem) {
        LOGGER.info("RWPlanner: solve() not implemented -> returning null");
        return null;
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
