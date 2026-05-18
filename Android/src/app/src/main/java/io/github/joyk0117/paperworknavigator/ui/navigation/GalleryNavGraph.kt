/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.joyk0117.paperworknavigator.ui.navigation

import android.os.Bundle
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.EaseOutExpo
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.github.joyk0117.paperworknavigator.GalleryEvent
import io.github.joyk0117.paperworknavigator.customtasks.common.CustomTaskData
import io.github.joyk0117.paperworknavigator.customtasks.common.CustomTaskDataForBuiltinTask
import io.github.joyk0117.paperworknavigator.data.BuiltInTaskId
import io.github.joyk0117.paperworknavigator.data.ConfigKeys
import io.github.joyk0117.paperworknavigator.data.EMPTY_MODEL
import io.github.joyk0117.paperworknavigator.data.Model
import io.github.joyk0117.paperworknavigator.data.ModelCapability
import io.github.joyk0117.paperworknavigator.data.ModelDownloadStatusType
import io.github.joyk0117.paperworknavigator.data.Task
import io.github.joyk0117.paperworknavigator.data.convertValueToTargetType
import io.github.joyk0117.paperworknavigator.data.isLegacyTasks
import io.github.joyk0117.paperworknavigator.firebaseAnalytics
import io.github.joyk0117.paperworknavigator.ui.benchmark.BenchmarkScreen
import io.github.joyk0117.paperworknavigator.ui.common.ConfigDialog
import io.github.joyk0117.paperworknavigator.ui.common.ErrorDialog
import io.github.joyk0117.paperworknavigator.ui.common.ModelPageAppBar
import io.github.joyk0117.paperworknavigator.ui.common.chat.ModelDownloadStatusInfoPanel
import io.github.joyk0117.paperworknavigator.ui.home.HomeScreen
import io.github.joyk0117.paperworknavigator.ui.home.PromoScreenGm4
import io.github.joyk0117.paperworknavigator.ui.modelmanager.GlobalModelManager
import io.github.joyk0117.paperworknavigator.ui.modelmanager.ModelInitializationStatusType
import io.github.joyk0117.paperworknavigator.ui.modelmanager.ModelManager
import io.github.joyk0117.paperworknavigator.ui.modelmanager.ModelManagerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "AGGalleryNavGraph"
private const val ROUTE_DOCUMENT_REVIEW = "document_review_entry"
private const val ROUTE_HOMESCREEN = "homepage"
private const val ROUTE_MODEL_LIST = "model_list"
private const val ROUTE_MODEL = "route_model"
private const val ROUTE_BENCHMARK = "benchmark"
private const val ROUTE_MODEL_MANAGER = "model_manager"
private const val ENTER_ANIMATION_DURATION_MS = 500
private val ENTER_ANIMATION_EASING = EaseOutExpo
private const val ENTER_ANIMATION_DELAY_MS = 100

private const val EXIT_ANIMATION_DURATION_MS = 500
private val EXIT_ANIMATION_EASING = EaseOutExpo

private fun enterTween(): FiniteAnimationSpec<IntOffset> {
  return tween(
    ENTER_ANIMATION_DURATION_MS,
    easing = ENTER_ANIMATION_EASING,
    delayMillis = ENTER_ANIMATION_DELAY_MS,
  )
}

private fun exitTween(): FiniteAnimationSpec<IntOffset> {
  return tween(EXIT_ANIMATION_DURATION_MS, easing = EXIT_ANIMATION_EASING)
}

private fun AnimatedContentTransitionScope<*>.slideEnter(): EnterTransition {
  return slideIntoContainer(
    animationSpec = enterTween(),
    towards = AnimatedContentTransitionScope.SlideDirection.Left,
  )
}

private fun AnimatedContentTransitionScope<*>.slideExit(): ExitTransition {
  return slideOutOfContainer(
    animationSpec = exitTween(),
    towards = AnimatedContentTransitionScope.SlideDirection.Right,
  )
}

private fun AnimatedContentTransitionScope<*>.slideUpEnter(): EnterTransition {
  return slideIntoContainer(
    animationSpec = enterTween(),
    towards = AnimatedContentTransitionScope.SlideDirection.Up,
  )
}

private fun AnimatedContentTransitionScope<*>.slideDownExit(): ExitTransition {
  return slideOutOfContainer(
    animationSpec = exitTween(),
    towards = AnimatedContentTransitionScope.SlideDirection.Down,
  )
}

/** Navigation routes. */
@Composable
fun GalleryNavHost(
  navController: NavHostController,
  modifier: Modifier = Modifier,
  modelManagerViewModel: ModelManagerViewModel,
) {
  val lifecycleOwner = LocalLifecycleOwner.current
  var showModelManager by remember { mutableStateOf(false) }
  var pickedTask by remember { mutableStateOf<Task?>(null) }
  var enableHomeScreenAnimation by remember { mutableStateOf(true) }
  var enableModelListAnimation by remember { mutableStateOf(true) }
  var lastNavigatedModelName = remember { "" }

  // Track whether app is in foreground.
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_START,
        Lifecycle.Event.ON_RESUME -> {
          modelManagerViewModel.setAppInForeground(foreground = true)
        }
        Lifecycle.Event.ON_STOP,
        Lifecycle.Event.ON_PAUSE -> {
          modelManagerViewModel.setAppInForeground(foreground = false)
        }
        else -> {
          /* Do nothing for other events */
        }
      }
    }

    lifecycleOwner.lifecycle.addObserver(observer)

    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  NavHost(
    navController = navController,
    startDestination = ROUTE_DOCUMENT_REVIEW,
    enterTransition = { EnterTransition.None },
    exitTransition = { ExitTransition.None },
  ) {
    // Document Review entry point — replaces HomeScreen as start destination.
    composable(route = ROUTE_DOCUMENT_REVIEW) {
      DocumentReviewEntryRoute(
        navController = navController,
        modelManagerViewModel = modelManagerViewModel,
      )
    }

    // Home screen.
    composable(route = ROUTE_HOMESCREEN) {
      // Create a state to trigger PromoScreen fade in animation.
      val promoId = "gm4"
      Box(modifier = modifier.fillMaxSize()) {
        var promoDismissed by remember { mutableStateOf(false) }

        val homeScreenContent: @Composable () -> Unit = {
          HomeScreen(
            modelManagerViewModel = modelManagerViewModel,
            tosViewModel = hiltViewModel(),
            enableAnimation = enableHomeScreenAnimation,
            navigateToTaskScreen = { task ->
              pickedTask = task
              enableModelListAnimation = true
              navController.navigate(ROUTE_MODEL_LIST)
              firebaseAnalytics?.logEvent(
                GalleryEvent.CAPABILITY_SELECT.id,
                Bundle().apply { putString("capability_name", task.id) },
              )
            },
            onModelsClicked = { navController.navigate(ROUTE_MODEL_MANAGER) },
            gm4 = true,
          )
        }

        // Show home page directly if promo has been viewed.
        if (modelManagerViewModel.dataStoreRepository.hasViewedPromo(promoId = promoId)) {
          homeScreenContent()
        }
        // If the promo has not been viewed, show promo screen first.
        else {
          AnimatedContent(
            targetState = promoDismissed,
            label = "PromoToHome",
            transitionSpec = { fadeIn() togetherWith fadeOut() },
          ) { dismissed ->
            if (dismissed) {
              homeScreenContent()
            } else {
              var startAnimation by remember { mutableStateOf(false) }
              LaunchedEffect(Unit) {
                delay(0L)
                startAnimation = true
              }
              AnimatedVisibility(
                visible = startAnimation,
                enter = scaleIn(initialScale = 1.05f, animationSpec = tween(durationMillis = 1000)),
              ) {
                PromoScreenGm4(
                  onDismiss = {
                    modelManagerViewModel.dataStoreRepository.addViewedPromoId(promoId = promoId)
                    promoDismissed = true
                  }
                )
              }
            }
          }
        }
      }
    }

    // Model list.
    composable(
      route = ROUTE_MODEL_LIST,
      enterTransition = {
        if (initialState.destination.route == ROUTE_HOMESCREEN) {
          slideEnter()
        } else {
          EnterTransition.None
        }
      },
      exitTransition = {
        if (targetState.destination.route == ROUTE_HOMESCREEN) {
          slideExit()
        } else {
          ExitTransition.None
        }
      },
    ) {
      pickedTask?.let {
        ModelManager(
          viewModel = modelManagerViewModel,
          task = it,
          enableAnimation = enableModelListAnimation,
          onModelClicked = { model ->
            navController.navigate("$ROUTE_MODEL/${it.id}/${model.name}")
          },
          onBenchmarkClicked = { model ->
            firebaseAnalytics?.logEvent(
              GalleryEvent.CAPABILITY_SELECT.id,
              Bundle().apply { putString("capability_name", "benchmark_${model.name}") },
            )
            navController.navigate("$ROUTE_BENCHMARK/${model.name}")
          },
          navigateUp = {
            enableHomeScreenAnimation = false
            navController.navigateUp()
          },
        )
      }
    }

    // Model page.
    composable(
      route = "$ROUTE_MODEL/{taskId}/{modelName}",
      arguments =
        listOf(
          navArgument("taskId") { type = NavType.StringType },
          navArgument("modelName") { type = NavType.StringType },
        ),
      enterTransition = { slideEnter() },
      exitTransition = { slideExit() },
    ) { backStackEntry ->
      val modelName = backStackEntry.arguments?.getString("modelName") ?: ""
      val taskId = backStackEntry.arguments?.getString("taskId") ?: ""
      val scope = rememberCoroutineScope()
      val context = LocalContext.current

      modelManagerViewModel.getModelByName(name = modelName)?.let { initialModel ->
        if (lastNavigatedModelName != modelName) {
          modelManagerViewModel.selectModel(initialModel)
          lastNavigatedModelName = modelName
        }

        val customTask = modelManagerViewModel.getCustomTaskByTaskId(id = taskId)
        if (customTask != null) {
          if (isLegacyTasks(customTask.task.id)) {
            customTask.MainScreen(
              data =
                CustomTaskDataForBuiltinTask(
                  modelManagerViewModel = modelManagerViewModel,
                  onNavUp = {
                    enableModelListAnimation = false
                    lastNavigatedModelName = ""
                    navController.navigateUp()
                  },
                )
            )
          } else {
            var disableAppBarControls by remember { mutableStateOf(false) }
            var hideTopBar by remember { mutableStateOf(false) }
            var customNavigateUpCallback by remember { mutableStateOf<(() -> Unit)?>(null) }
            CustomTaskScreen(
              task = customTask.task,
              modelManagerViewModel = modelManagerViewModel,
              onNavigateUp = {
                if (customNavigateUpCallback != null) {
                  customNavigateUpCallback?.invoke()
                } else {
                  enableModelListAnimation = false
                  lastNavigatedModelName = ""
                  navController.navigateUp()

                  // clean up all models.
                  for (curModel in customTask.task.models) {
                    val instanceToCleanUp = curModel.instance
                    scope.launch(Dispatchers.Default) {
                      modelManagerViewModel.cleanupModel(
                        context = context,
                        task = customTask.task,
                        model = curModel,
                        instanceToCleanUp = instanceToCleanUp,
                      )
                    }
                  }
                }
              },
              disableAppBarControls = disableAppBarControls,
              hideTopBar = hideTopBar,
              useThemeColor = customTask.task.useThemeColor,
            ) { bottomPadding ->
              customTask.MainScreen(
                data =
                  CustomTaskData(
                    modelManagerViewModel = modelManagerViewModel,
                    bottomPadding = bottomPadding,
                    setAppBarControlsDisabled = { disableAppBarControls = it },
                    setTopBarVisible = { hideTopBar = !it },
                    setCustomNavigateUpCallback = { customNavigateUpCallback = it },
                  )
              )
            }
          }
        }
      }
    }

    // Global model manager page.
    composable(
      route = ROUTE_MODEL_MANAGER,
      enterTransition = {
        if (
          initialState.destination.route?.startsWith(ROUTE_BENCHMARK) == true ||
            initialState.destination.route?.startsWith(ROUTE_MODEL) == true
        ) {
          null
        } else {
          slideUpEnter()
        }
      },
      exitTransition = {
        if (
          targetState.destination.route?.startsWith(ROUTE_BENCHMARK) == true ||
            targetState.destination.route?.startsWith(ROUTE_MODEL) == true
        ) {
          null
        } else {
          slideDownExit()
        }
      },
    ) { backStackEntry ->
      GlobalModelManager(
        viewModel = modelManagerViewModel,
        navigateUp = {
          enableHomeScreenAnimation = false
          navController.navigateUp()
        },
        onModelSelected = { task, model ->
          navController.navigate("$ROUTE_MODEL/${task.id}/${model.name}")
        },
        onBenchmarkClicked = { model ->
          firebaseAnalytics?.logEvent(
            GalleryEvent.CAPABILITY_SELECT.id,
            Bundle().apply { putString("capability_name", "benchmark_${model.name}") },
          )
          navController.navigate("$ROUTE_BENCHMARK/${model.name}")
        },
      )
    }

    // Benchmark creation page.
    composable(
      route = "$ROUTE_BENCHMARK/{modelName}",
      arguments = listOf(navArgument("modelName") { type = NavType.StringType }),
      enterTransition = { slideEnter() },
      exitTransition = { slideExit() },
    ) { backStackEntry ->
      val modelName = backStackEntry.arguments?.getString("modelName") ?: ""

      modelManagerViewModel.getModelByName(name = modelName)?.let { model ->
        BenchmarkScreen(
          initialModel = model,
          modelManagerViewModel = modelManagerViewModel,
          onBackClicked = {
            enableModelListAnimation = false
            navController.navigateUp()
          },
        )
      }
    }
  }

  // Handle incoming intents for deep links
  val intent = androidx.activity.compose.LocalActivity.current?.intent
  val data = intent?.data
  if (data != null) {
    intent.data = null
    Log.d(TAG, "navigation link clicked: $data")
    if (data.toString().startsWith("io.github.joyk0117.paperworknavigator://model/")) {
      if (data.pathSegments.size >= 2) {
        val taskId = data.pathSegments.get(data.pathSegments.size - 2)
        val modelName = data.pathSegments.last()
        modelManagerViewModel.getModelByName(name = modelName)?.let { model ->
          navController.navigate("$ROUTE_MODEL/${taskId}/${model.name}")
        }
      } else {
        Log.e(TAG, "Malformed deep link URI received: $data")
      }
    } else if (data.toString() == "io.github.joyk0117.paperworknavigator://global_model_manager") {
      navController.navigate(ROUTE_MODEL_MANAGER)
    }
  }
}

/**
 * Start-destination composable that shows the Document Review task (S-01) directly.
 *
 * - Auto-selects the first Document Review model once the allowlist is loaded.
 * - Initialises the model when download completes (mirrors CustomTaskScreen logic).
 * - [BackHandler] is enabled only on S-02/S-03 so that pressing back on S-01 falls
 *   through to the system default, which exits the app (nothing else is in the backstack).
 */
@Composable
private fun DocumentReviewEntryRoute(
  navController: NavHostController,
  modelManagerViewModel: ModelManagerViewModel,
  modifier: Modifier = Modifier,
) {
  val customTask = modelManagerViewModel.getCustomTaskByTaskId(BuiltInTaskId.DOCUMENT_REVIEW)
    ?: return

  val context = LocalContext.current
  val modelManagerUiState by modelManagerViewModel.uiState.collectAsState()
  val selectedModel = modelManagerUiState.selectedModel
  var disableAppBarControls by remember { mutableStateOf(false) }
  var customNavigateUpCallback by remember { mutableStateOf<(() -> Unit)?>(null) }
  var showErrorDialog by remember { mutableStateOf(false) }

  // Auto-select the first Document Review model once the allowlist finishes loading.
  LaunchedEffect(modelManagerUiState.loadingModelAllowlist) {
    if (!modelManagerUiState.loadingModelAllowlist && selectedModel == EMPTY_MODEL) {
      customTask.task.models.firstOrNull()?.let { modelManagerViewModel.selectModel(it) }
    }
  }

  // Clean up the previous model and initialize the new one when selectedModel changes.
  // Cleanup must happen before init to avoid loading two models simultaneously (OOM on 12GB RAM).
  var previousModel by remember { mutableStateOf<Model?>(null) }
  val curDownloadStatus = modelManagerUiState.modelDownloadStatus[selectedModel.name]
  LaunchedEffect(selectedModel.name, curDownloadStatus?.status) {
    val prevModel = previousModel
    if (prevModel != null && prevModel.name != selectedModel.name) {
      val instanceToCleanUp = prevModel.instance
      withContext(Dispatchers.Default) {
        modelManagerViewModel.cleanupModel(
          context = context,
          task = customTask.task,
          model = prevModel,
          instanceToCleanUp = instanceToCleanUp,
        )
        // Encourage native memory reclamation before loading the next model.
        System.gc()
      }
    }
    previousModel = selectedModel
    if (curDownloadStatus?.status == ModelDownloadStatusType.SUCCEEDED) {
      modelManagerViewModel.initializeModel(
        context = context,
        task = customTask.task,
        model = selectedModel,
      )
    }
  }

  // Show error dialog when model initialization fails (mirrors CustomTaskScreen logic).
  val modelInitializationStatus = modelManagerUiState.modelInitializationStatus[selectedModel.name]
  LaunchedEffect(modelInitializationStatus) {
    showErrorDialog = modelInitializationStatus?.status == ModelInitializationStatusType.ERROR
  }

  // Only consume back on S-02/S-03; on S-01 the system default exits the app.
  BackHandler(enabled = customNavigateUpCallback != null) {
    customNavigateUpCallback?.invoke()
  }

  Scaffold(
    modifier = modifier,
    topBar = {
      DocumentReviewTopBar(
        onNavigateToModelManager = { navController.navigate(ROUTE_MODEL_MANAGER) },
        onNavigateUp = customNavigateUpCallback,
        enabled = !disableAppBarControls,
        showModelManagerButton = false,
        model = selectedModel,
        task = customTask.task,
        modelManagerViewModel = modelManagerViewModel,
      )
    },
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .padding(
          top = innerPadding.calculateTopPadding(),
          start = innerPadding.calculateStartPadding(LocalLayoutDirection.current),
          end = innerPadding.calculateStartPadding(LocalLayoutDirection.current),
        )
    ) {
      customTask.MainScreen(
        data = CustomTaskData(
          modelManagerViewModel = modelManagerViewModel,
          bottomPadding = innerPadding.calculateBottomPadding(),
          setAppBarControlsDisabled = { disableAppBarControls = it },
          setTopBarVisible = { /* AppBar is always shown in this route */ },
          setCustomNavigateUpCallback = { customNavigateUpCallback = it },
        )
      )
    }
  }

  if (showErrorDialog) {
    ErrorDialog(
      error = modelInitializationStatus?.error ?: "",
      onDismiss = { showErrorDialog = false },
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentReviewTopBar(
  onNavigateToModelManager: () -> Unit,
  onNavigateUp: (() -> Unit)?,
  enabled: Boolean,
  showModelManagerButton: Boolean = true,
  model: Model,
  task: Task,
  modelManagerViewModel: ModelManagerViewModel,
) {
  var showConfigDialog by remember { mutableStateOf(false) }
  val modelManagerUiState by modelManagerViewModel.uiState.collectAsState()
  val context = LocalContext.current
  val curDownloadStatus = modelManagerUiState.modelDownloadStatus[model.name]
  val modelInitializationStatus = modelManagerUiState.modelInitializationStatus[model.name]
  val isModelInitializing =
    modelInitializationStatus?.status == ModelInitializationStatusType.INITIALIZING
  val isModelInitialized =
    modelInitializationStatus?.status == ModelInitializationStatusType.INITIALIZED
  val downloadSucceeded = curDownloadStatus?.status == ModelDownloadStatusType.SUCCEEDED
  val showConfigButton = model.configs.isNotEmpty() && downloadSucceeded

  TopAppBar(
    title = { Text("Paperwork Navigator") },
    navigationIcon = {
      if (onNavigateUp != null) {
        IconButton(onClick = onNavigateUp, enabled = enabled) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "戻る",
          )
        }
      }
    },
    actions = {
      if (showConfigButton) {
        IconButton(
          onClick = { showConfigDialog = true },
          enabled = enabled && isModelInitialized && !isModelInitializing,
        ) {
          Icon(
            imageVector = Icons.Rounded.Tune,
            contentDescription = "モデル設定",
          )
        }
      }
      if (showModelManagerButton) {
        IconButton(onClick = onNavigateToModelManager, enabled = enabled) {
          Icon(
            imageVector = Icons.Rounded.Settings,
            contentDescription = "モデルマネージャー",
          )
        }
      }
    },
  )

  if (showConfigDialog) {
    val modelConfigs = model.configs.toMutableList()
    if (task.id != BuiltInTaskId.LLM_TINY_GARDEN) {
      modelConfigs.removeIf { it.key == ConfigKeys.RESET_CONVERSATION_TURN_COUNT }
    }
    if (!task.allowCapability(ModelCapability.LLM_THINKING, model)) {
      modelConfigs.removeIf { it.key == ConfigKeys.ENABLE_THINKING }
    }
    var supportsSpeculativeDecoding = false
    try {
      com.google.ai.edge.litertlm.Capabilities(model.getPath(context)).use {
        supportsSpeculativeDecoding = it.hasSpeculativeDecodingSupport()
      }
    } catch (e: Exception) {
      // モデルファイル未ダウンロード時などは非対応として扱う
    }
    if (
      !supportsSpeculativeDecoding ||
        !task.allowCapability(ModelCapability.SPECULATIVE_DECODING, model)
    ) {
      modelConfigs.removeIf { it.key == ConfigKeys.ENABLE_SPECULATIVE_DECODING }
    }
    ConfigDialog(
      title = "Configurations",
      configs = modelConfigs,
      initialValues = model.configValues,
      onDismissed = { showConfigDialog = false },
      onOk = { curConfigValues, _, _ ->
        showConfigDialog = false
        var same = true
        var needReinitialization = false
        for (config in modelConfigs) {
          val key = config.key.label
          val oldValue =
            convertValueToTargetType(
              value = model.configValues.getValue(key),
              valueType = config.valueType,
            )
          val newValue =
            convertValueToTargetType(
              value = curConfigValues.getValue(key),
              valueType = config.valueType,
            )
          if (oldValue != newValue) {
            same = false
            if (config.needReinitialization) {
              needReinitialization = true
            }
            break
          }
        }
        if (!same) {
          model.prevConfigValues = model.configValues
          model.configValues = curConfigValues
          modelManagerViewModel.updateConfigValuesUpdateTrigger()
          if (needReinitialization && !task.handleModelConfigChangesInTask) {
            modelManagerViewModel.initializeModel(
              context = context,
              task = task,
              model = model,
              force = true,
            )
          }
        }
      },
    )
  }
}

@Composable
private fun CustomTaskScreen(
  task: Task,
  modelManagerViewModel: ModelManagerViewModel,
  disableAppBarControls: Boolean,
  hideTopBar: Boolean,
  useThemeColor: Boolean,
  onNavigateUp: () -> Unit,
  content: @Composable (bottomPadding: Dp) -> Unit,
) {
  val modelManagerUiState by modelManagerViewModel.uiState.collectAsState()
  val selectedModel = modelManagerUiState.selectedModel
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  var navigatingUp by remember { mutableStateOf(false) }
  var showErrorDialog by remember { mutableStateOf(false) }
  var appBarHeight by remember { mutableIntStateOf(0) }

  val handleNavigateUp = {
    navigatingUp = true
    onNavigateUp()
  }

  // Handle system's edge swipe.
  BackHandler { handleNavigateUp() }

  // Initialize model when model/download state changes.
  val curDownloadStatus = modelManagerUiState.modelDownloadStatus[selectedModel.name]
  LaunchedEffect(curDownloadStatus, selectedModel.name) {
    if (!navigatingUp) {
      if (curDownloadStatus?.status == ModelDownloadStatusType.SUCCEEDED) {
        Log.d(
          TAG,
          "Initializing model '${selectedModel.name}' from CustomTaskScreen launched effect",
        )
        modelManagerViewModel.initializeModel(context, task = task, model = selectedModel)
      }
    }
  }

  val modelInitializationStatus = modelManagerUiState.modelInitializationStatus[selectedModel.name]
  LaunchedEffect(modelInitializationStatus) {
    showErrorDialog = modelInitializationStatus?.status == ModelInitializationStatusType.ERROR
  }

  Scaffold(
    topBar = {
      AnimatedVisibility(
        !hideTopBar,
        enter = slideInVertically { -it },
        exit = slideOutVertically { -it },
      ) {
        ModelPageAppBar(
          task = task,
          model = selectedModel,
          modelManagerViewModel = modelManagerViewModel,
          inProgress = disableAppBarControls,
          modelPreparing = disableAppBarControls,
          canShowResetSessionButton = false,
          useThemeColor = useThemeColor,
          modifier =
            Modifier.onGloballyPositioned { coordinates -> appBarHeight = coordinates.size.height },
          hideModelSelector = task.models.size <= 1,
          onConfigChanged = { _, _ -> },
          onBackClicked = { handleNavigateUp() },
          onModelSelected = { prevModel, newSelectedModel ->
            val instanceToCleanUp = prevModel.instance
            scope.launch(Dispatchers.Default) {
              // Clean up prev model.
              if (prevModel.name != newSelectedModel.name) {
                modelManagerViewModel.cleanupModel(
                  context = context,
                  task = task,
                  model = prevModel,
                  instanceToCleanUp = instanceToCleanUp,
                )
              }

              // Update selected model.
              Log.d(TAG, "from model picker. new: ${newSelectedModel.name}")
              modelManagerViewModel.selectModel(model = newSelectedModel)
            }
          },
        )
      }
    }
  ) { innerPadding ->
    // Calculate the target height in Dp for the content's top padding.
    val targetPaddingDp =
      if (!hideTopBar && appBarHeight > 0) {
        // Convert measured pixel height to Dp
        with(LocalDensity.current) { appBarHeight.toDp() }
      } else {
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
      }

    // Animate the actual top padding value.
    val animatedTopPadding by
      animateDpAsState(
        targetValue = targetPaddingDp,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "TopPaddingAnimation",
      )

    Box(
      modifier =
        Modifier.padding(
          top = if (!hideTopBar) innerPadding.calculateTopPadding() else animatedTopPadding,
          start = innerPadding.calculateStartPadding(LocalLayoutDirection.current),
          end = innerPadding.calculateStartPadding(LocalLayoutDirection.current),
        )
    ) {
      val curModelDownloadStatus = modelManagerUiState.modelDownloadStatus[selectedModel.name]
      AnimatedContent(
        targetState = curModelDownloadStatus?.status == ModelDownloadStatusType.SUCCEEDED
      ) { targetState ->
        when (targetState) {
          // Main UI when model is downloaded.
          true -> content(innerPadding.calculateBottomPadding())
          // Model download
          false ->
            ModelDownloadStatusInfoPanel(
              model = selectedModel,
              task = task,
              modelManagerViewModel = modelManagerViewModel,
            )
        }
      }
    }
  }

  if (showErrorDialog) {
    ErrorDialog(
      error = modelInitializationStatus?.error ?: "",
      onDismiss = {
        showErrorDialog = false
        onNavigateUp()
      },
    )
  }
}
