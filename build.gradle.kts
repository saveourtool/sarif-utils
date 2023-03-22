import com.saveourtool.sarifutils.buildutils.configureDetekt
import com.saveourtool.sarifutils.buildutils.configureDiktat
import com.saveourtool.sarifutils.buildutils.configurePublishing
import com.saveourtool.sarifutils.buildutils.configureVersioning
import com.saveourtool.sarifutils.buildutils.createDetektTask
import com.saveourtool.sarifutils.buildutils.installGitHooks

plugins {
    `maven-publish`
}

// version generation
configureVersioning()
// checks and validations
configureDiktat()
configureDetekt()
createDetektTask()
installGitHooks()
configurePublishing()
