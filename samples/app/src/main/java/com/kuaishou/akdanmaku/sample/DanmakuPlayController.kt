package com.kuaishou.akdanmaku.sample

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.graphics.RectF
import android.os.SystemClock
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.*
import androidx.appcompat.widget.SwitchCompat
import com.kuaishou.akdanmaku.library.DanmakuConfig
import com.kuaishou.akdanmaku.library.data.DanmakuItemData
import com.kuaishou.akdanmaku.library.ecs.DanmakuEngine
import com.kuaishou.akdanmaku.library.ecs.component.action.Actions
import com.kuaishou.akdanmaku.library.ecs.component.filter.*
import com.kuaishou.akdanmaku.library.ui.DanmakuPlayer
import kotlin.random.Random

@SuppressLint("ClickableViewAccessibility")
class DanmakuPlayController(
  activity: Activity,
  private val danmakuPlayer: DanmakuPlayer,
  byStep: Boolean
) {

  private val playButton = activity.findViewById<ImageView>(R.id.playButton)
  private val visibilitySwitch = activity.findViewById<SwitchCompat>(R.id.visibilitySwitch)
  private val speedView = activity.findViewById<TextView>(R.id.speedView)
  private val danmakuTime = activity.findViewById<TextView>(R.id.txtDanmakuTime)
  private val txtInfo = activity.findViewById<TextView>(R.id.txtPlayInfo)
  private val txtTimer = activity.findViewById<TextView>(R.id.txtTimer)
  private val seekBar = activity.findViewById<SeekBar>(R.id.seekbar)
  private val sendButton = activity.findViewById<View>(R.id.send)
  private val sizeButton = activity.findViewById<View>(R.id.changeSize)
  private val stepMsInput = activity.findViewById<EditText>(R.id.stepDeltaTime)
  private val stepButton = activity.findViewById<Button>(R.id.stepButton)

  private val danmakuView = activity.findViewById<View>(R.id.danmakuView)

  private val rollingSwitch = activity.findViewById<SwitchCompat>(R.id.rollingSwitch)
  private val topSwitch = activity.findViewById<SwitchCompat>(R.id.topSwitch)
  private val bottomSwitch = activity.findViewById<SwitchCompat>(R.id.bottomSwitch)
  private val colorSwitch = activity.findViewById<SwitchCompat>(R.id.colorSwitch)
  private val overlapSwitch = activity.findViewById<SwitchCompat>(R.id.overlapSwitch)

  private var startTime = SystemClock.uptimeMillis()
  private var tracking = false

  private val seekBarUpdater: Runnable = object : Runnable {
    override fun run() {
      if (!tracking) {
        val time = danmakuPlayer.getCurrentTimeMs()
        seekBar.progress = time.toInt()
        updateTimeText(danmakuTime, time)
      }
      updateTimeText(txtTimer, SystemClock.uptimeMillis() - startTime)
      seekBar.postOnAnimation(this)
    }
  }
  private val hitRect = RectF()
  private val gestureDetector = GestureDetector(danmakuView.context, object : GestureDetector.SimpleOnGestureListener() {

    override fun onDown(e: MotionEvent?): Boolean {
      return true
    }

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
      val range = ViewConfiguration.get(activity).scaledTouchSlop
      hitRect.set(e.x - range, e.y - range, e.x + range, e.y + range)
      val danmakus = danmakuPlayer.getDanmakusInRect(hitRect)
      val item = danmakus?.firstOrNull()
      danmakuPlayer.hold(item)
      if (item == null && config.alpha != 1f) {
        config = config.copy(alpha = 1f)
        danmakuPlayer.updateConfig(config)
      } else if (item != null && config.alpha == 1f) {
        config = config.copy(alpha = 0.3f)
        danmakuPlayer.updateConfig(config)
      }
      return true
    }
  })

  private var isPlaying = false
  private var speedLevel = 0
  private val colorFilter = TextColorFilter()
  private var dataFilters = emptyMap<Int, DanmakuFilter>()
  private var config = DanmakuConfig().apply {
    dataFilter = createDataFilters()
    dataFilters = dataFilter.associateBy { it.filterParams }
    layoutFilter = createLayoutFilters()
    textSizeScale = 0.8f
  }

  private val textScaleList = listOf(
    0.8f, 1.0f, 1.25f, 2f
  )
  private var textScaleIndex = 0

  private val infoUpdater: Runnable = object : Runnable {

    @SuppressLint("SetTextI18n")
    override fun run() {
      val hit = danmakuPlayer.cacheHit ?: return
      txtInfo.text = "CacheHit: ${"%.2f%%".format(hit.value * 100)} ${hit.num}/${hit.den}"
      txtInfo.postOnAnimation(this)
    }
  }

  init {
    playButton.setOnClickListener {
      if (isPlaying) {
        pause()
      } else {
        start()
      }
    }
    visibilitySwitch.setOnCheckedChangeListener { _, isChecked ->
      setVisibility(isChecked)
    }
    speedView.setOnClickListener { updateSpeed() }
    sendButton.setOnClickListener { sendDanmaku() }
    sizeButton.setOnClickListener { switchTextScale() }
    danmakuView.setOnTouchListener { _, event ->
      return@setOnTouchListener gestureDetector.onTouchEvent(event)
    }
    txtInfo.postOnAnimation(infoUpdater)
    stepButton.setOnClickListener {
      val deltaTimeMs = stepMsInput.text.toString().toIntOrNull() ?: 0
      DanmakuPlayer.isManualStep = deltaTimeMs > 0
      danmakuPlayer.step(deltaTimeMs)
    }

    initSeekBar()
    stepMsInput.visibility = if (byStep) View.VISIBLE else View.GONE
    stepButton.visibility = if (byStep) View.VISIBLE else View.GONE

    rollingSwitch.setOnCheckedChangeListener { _, isChecked ->
      switchTypeFilter(isChecked, DanmakuItemData.DANMAKU_MODE_ROLLING)
    }
    topSwitch.setOnCheckedChangeListener { _, isChecked ->
      switchTypeFilter(isChecked, DanmakuItemData.DANMAKU_MODE_CENTER_TOP)
    }
    bottomSwitch.setOnCheckedChangeListener { _, isChecked ->
      switchTypeFilter(isChecked, DanmakuItemData.DANMAKU_MODE_CENTER_BOTTOM)
    }

    colorSwitch.setOnCheckedChangeListener { _, isChecked ->
      colorFilter.filterColor.clear()
      if (!isChecked) {
        colorFilter.filterColor.add(0xFFFFFF)
      }
      config.updateFilter()
      danmakuPlayer.updateConfig(config)
    }
    overlapSwitch.setOnCheckedChangeListener { _, isChecked ->
      config = config.copy(allowOverlap = isChecked)
      danmakuPlayer.updateConfig(config)
    }
  }

  private fun switchTypeFilter(show: Boolean, type: Int) {

    (dataFilters[DanmakuFilters.FILTER_TYPE_TYPE] as? TypeFilter)?.let { filter ->
      if (show) filter.removeFilterItem(type)
      else filter.addFilterItem(type)
      config.updateFilter()
      Log.w(DanmakuEngine.TAG, "[Controller] updateFilter visibility: ${config.visibility}")
      danmakuPlayer.updateConfig(config)
    }
  }

  private fun initSeekBar() {
    seekBar.max = 180_000
    seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
      override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        if (fromUser) {
          updateTimeText(danmakuTime, progress.toLong())
        }
      }

      override fun onStartTrackingTouch(seekBar: SeekBar?) {
        tracking = true
      }

      override fun onStopTrackingTouch(seekBar: SeekBar?) {
        val pos = seekBar?.progress ?: 0
        danmakuPlayer.seekTo(pos.toLong())
        startTime = SystemClock.uptimeMillis() - pos
        tracking = false
      }
    })
  }

  @SuppressLint("SetTextI18n")
  private fun updateTimeText(view: TextView, timeMills: Long) {
    view.text = "%02d:%02d.%03d".format(timeMills / 60_000, timeMills % 60_000 / 1000, timeMills % 1000)
  }

  fun start() {
    isPlaying = true
    danmakuPlayer.start(config)
    playButton.setImageResource(android.R.drawable.ic_media_pause)
    seekBar.post(seekBarUpdater)
    startTime = SystemClock.uptimeMillis() - seekBar.progress
  }

  fun pause() {
    isPlaying = false
    danmakuPlayer.pause()
    playButton.setImageResource(android.R.drawable.ic_media_play)
    seekBar.removeCallbacks(seekBarUpdater)
  }

  private fun setVisibility(visible: Boolean) {
    config = config.copy(visibility = visible)
    danmakuPlayer.updateConfig(config)
  }

  @SuppressLint("SetTextI18n")
  private fun updateSpeed() {
    speedLevel = (speedLevel + 1) % 3
    val speed = speedLevel + 1.0f
    speedView.text = "Speed=X$speed"
    danmakuPlayer.updatePlaySpeed(speed)
  }

  private fun sendDanmaku() {
    val danmaku = DanmakuItemData(
      Random.nextLong(),
      danmakuPlayer.getCurrentTimeMs() + 500,
      "这是我自己发送的内容(*^▽^*)😄",
      DanmakuItemData.DANMAKU_MODE_ROLLING,
      25,
      Color.WHITE,
      9,
      DanmakuItemData.DANMAKU_STYLE_ICON_UP,
      9
    )
    val item = danmakuPlayer.obtainItem(danmaku)
    val sequenceAction = Actions.sequence(
      Actions.rotateBy(360f, 1000L),
      Actions.scaleTo(1.5f, 1.5f, 500L),
      Actions.scaleTo(0.8f, 0.8f, 300L)
    )
    item.addAction(
      Actions.moveBy(0f, 300f, 1735L),
      sequenceAction,
      Actions.sequence(Actions.fadeOut(500L), Actions.fadeIn(300L))
    )
    danmakuPlayer.send(item)
  }

  private fun switchTextScale() {
    textScaleIndex = (textScaleIndex + 1) % textScaleList.size
    config = config.copy(textSizeScale = textScaleList[textScaleIndex])
    danmakuPlayer.updateConfig(config)
  }

  private fun createDataFilters(): List<DanmakuDataFilter> =
    listOf(
      TypeFilter(),
      colorFilter,
      UserIdFilter(),
      GuestFilter(),
      BlockedTextFilter { it == 0L },
      DuplicateMergedFilter()
    )

  private fun createLayoutFilters(): List<DanmakuLayoutFilter> = emptyList()
}
