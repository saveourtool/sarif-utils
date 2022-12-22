import com.saveourtool.sarifutils.buildutils.configureDiktat
import com.saveourtool.sarifutils.buildutils.configureVersioning
import com.saveourtool.sarifutils.buildutils.createDetektTask
import com.saveourtool.sarifutils.buildutils.installGitHooks
import com.saveourtool.sarifutils.buildutils.configurePublishing

plugins {
    `maven-publish`
}

// version generation
configureVersioning()
// checks and validations
configureDiktat()
createDetektTask()
installGitHooks()
configurePublishing()
