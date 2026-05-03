package com.dumbermini.apps.compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.dumbermini.apps.compose.appDetail.navigation.appDetail
import com.dumbermini.apps.compose.appDetail.navigation.navigateToAppDetail
import com.dumbermini.apps.compose.appList.navigation.AppList
import com.dumbermini.apps.compose.appList.navigation.appList
import com.dumbermini.apps.compose.appList.navigation.navigateToAppList
import com.dumbermini.apps.compose.home.navigation.home
import com.dumbermini.apps.compose.repoDetail.navigation.navigateToRepoDetail
import com.dumbermini.apps.compose.repoDetail.navigation.repoDetail
import com.dumbermini.apps.compose.repoEdit.navigation.navigateToRepoEdit
import com.dumbermini.apps.compose.repoEdit.navigation.repoEdit
import com.dumbermini.apps.compose.repoList.navigation.navigateToRepoList
import com.dumbermini.apps.compose.repoList.navigation.repoList
import com.dumbermini.apps.compose.settings.navigation.navigateToSettings
import com.dumbermini.apps.compose.settings.navigation.settings
import com.dumbermini.apps.compose.theme.DroidifyTheme
import com.dumbermini.apps.data.RepoRepository
import com.dumbermini.apps.model.Repository
import com.dumbermini.apps.utility.common.requestNotificationPermission
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainComposeActivity : ComponentActivity() {

    @Inject
    lateinit var repository: RepoRepository

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            if (repository.repos.first().isEmpty()) {
                Repository.defaultRepositories.forEach {
                    repository.insertRepo(it.address, it.fingerprint, null, null, it.name, it.description)
                }
            }
        }
        enableEdgeToEdge()
        requestNotificationPermission(request = notificationPermission::launch)
        setContent {
            DroidifyTheme {
                val navController = rememberNavController()
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavHost(
                        modifier = Modifier.padding(innerPadding),
                        navController = navController,
                        startDestination = AppList,
                    ) {
                        home(
                            onNavigateToApps = { navController.navigateToAppList() },
                            onNavigateToRepos = { navController.navigateToRepoList() },
                            onNavigateToSettings = { navController.navigateToSettings() },
                        )
                        appList(
                            onAppClick = { packageName ->
                                navController.navigateToAppDetail(packageName)
                            },
                            onNavigateToRepos = { navController.navigateToRepoList() },
                            onNavigateToSettings = { navController.navigateToSettings() },
                        )

                        repoList(
                            onRepoClick = { repoId -> navController.navigateToRepoDetail(repoId) },
                            onBackClick = { navController.popBackStack() }
                        )

                        appDetail(
                            onBackClick = { navController.popBackStack() },
                        )

                        repoDetail(
                            onBackClick = { navController.popBackStack() },
                            onEditClick = { repoId ->
                                navController.navigateToRepoEdit(repoId)
                            },
                        )

                        repoEdit(onBackClick = { navController.popBackStack() })

                        settings(onBackClick = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}
