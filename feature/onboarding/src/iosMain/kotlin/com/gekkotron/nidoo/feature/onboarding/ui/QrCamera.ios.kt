package com.gekkotron.nidoo.feature.onboarding.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.AVMetadataObjectTypeQRCode
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIColor
import platform.UIKit.UIView
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create

/**
 * The back camera with an AVFoundation QR reader (iOS spec §7.5). Every decoded code goes to [onText], repeats included:
 * the ViewModel ignores a code it already tried, as it does for CameraX on Android. Without a camera (the Simulator)
 * the view stays black.
 */
@Composable
internal actual fun QrCamera(modifier: Modifier, onText: (String) -> Unit) {
    val current by rememberUpdatedState(onText)
    val scanner = remember { QrScanner { current(it) } }
    DisposableEffect(scanner) {
        scanner.start()
        onDispose { scanner.stop() }
    }
    UIKitView(factory = { scanner.view }, modifier = modifier)
}

@OptIn(ExperimentalForeignApi::class)
private class QrScanner(onText: (String) -> Unit) {
    private val session = AVCaptureSession()
    private val delegate = MetadataDelegate(onText) // the output holds its delegate weakly
    private val queue = dispatch_queue_create("com.gekkotron.nidoo.qr", null)
    val view = PreviewView(session)

    init {
        configure()
    }

    private fun configure() {
        val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo) ?: return
        val input = AVCaptureDeviceInput.deviceInputWithDevice(device, error = null) as? AVCaptureDeviceInput ?: return
        if (!session.canAddInput(input)) return
        session.addInput(input)
        val output = AVCaptureMetadataOutput()
        if (!session.canAddOutput(output)) return
        session.addOutput(output)
        output.setMetadataObjectsDelegate(delegate, queue = dispatch_get_main_queue())
        // The QR type is only listed once the output is attached to a session with a camera.
        if (AVMetadataObjectTypeQRCode in output.availableMetadataObjectTypes) {
            output.metadataObjectTypes = listOf(AVMetadataObjectTypeQRCode)
        }
    }

    /** Apple: startRunning blocks, so never on the main thread. */
    fun start() = dispatch_async(queue) { session.startRunning() }

    fun stop() = dispatch_async(queue) { session.stopRunning() }
}

private class MetadataDelegate(private val onText: (String) -> Unit) : NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {
    override fun captureOutput(output: AVCaptureOutput, didOutputMetadataObjects: List<*>, fromConnection: AVCaptureConnection) {
        didOutputMetadataObjects.forEach { code -> (code as? AVMetadataMachineReadableCodeObject)?.stringValue?.let(onText) }
    }
}

/** A view whose preview layer always fills it (a CALayer does not follow its view's size by itself). */
@OptIn(ExperimentalForeignApi::class)
private class PreviewView(session: AVCaptureSession) : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    private val preview = AVCaptureVideoPreviewLayer(session = session).apply { videoGravity = AVLayerVideoGravityResizeAspectFill }

    init {
        backgroundColor = UIColor.blackColor
        layer.addSublayer(preview)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        preview.frame = bounds
    }
}
