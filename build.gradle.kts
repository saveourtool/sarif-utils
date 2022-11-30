import com.saveourtool.sariffixpatches.buildutils.configureDiktat
import com.saveourtool.sariffixpatches.buildutils.configureVersioning
import com.saveourtool.sariffixpatches.buildutils.createDetektTask
import com.saveourtool.sariffixpatches.buildutils.installGitHooks

// version generation
configureVersioning()
// checks and validations
configureDiktat()
createDetektTask()
installGitHooks()
