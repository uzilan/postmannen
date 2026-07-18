package postmannen

import com.googlecode.lanterna.bundle.LanternaThemes
import com.googlecode.lanterna.gui2.MultiWindowTextGUI
import com.googlecode.lanterna.screen.TerminalScreen
import com.googlecode.lanterna.terminal.DefaultTerminalFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import postmannen.service.PostmanApiServiceImpl
import postmannen.ui.App
import postmannen.viewmodel.AppViewModel
import kotlin.system.exitProcess

fun main() = runBlocking {
    val apiKey = System.getenv("POSTMAN_API_KEY")
    if (apiKey.isNullOrBlank()) {
        System.err.println("POSTMAN_API_KEY environment variable is required.")
        exitProcess(1)
    }

    val terminal = DefaultTerminalFactory().createTerminal()
    val screen = TerminalScreen(terminal)
    screen.startScreen()
    val gui = MultiWindowTextGUI(screen)
    gui.setTheme(LanternaThemes.getRegisteredTheme("businessmachine"))

    val scope = CoroutineScope(Dispatchers.Default)
    val viewModel = AppViewModel(PostmanApiServiceImpl(apiKey), scope)

    viewModel.loadWorkspaces()

    App(gui, screen, viewModel, scope).run()

    scope.cancel()
    screen.stopScreen()
}
