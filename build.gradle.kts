import com.saveourtool.sarifutils.buildutils.configureDiktat
import com.saveourtool.sarifutils.buildutils.configureVersioning
import com.saveourtool.sarifutils.buildutils.createDetektTask
import com.saveourtool.sarifutils.buildutils.installGitHooks

// version generation
configureVersioning()
// checks and validations
configureDiktat()
createDetektTask()
installGitHooks()
