package com.example.first.engine

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection

object AudioEngineFactory {

    enum class EngineType(val key: String) {
        NORMAL("NORMAL"),
        HI_RES("HI_RES"),
        USB_DAC("USB_DAC")          // USB exclusive / bit-perfect path
    }

    fun createEngine(context: Context, type: EngineType): IAudioEngine {
        return when (type) {
            EngineType.NORMAL  -> MediaPlayerEngine()
            EngineType.HI_RES  -> NativeHiResEngine()
            EngineType.USB_DAC -> NativeHiResEngine()  // default; use createUsbEngine for USB
        }
    }

    /**
     * Create a USB exclusive mode engine.
     * Call this when a USB DAC is connected and user has enabled USB-exclusive mode.
     */
    fun createUsbEngine(
        context: Context,
        usbDevice: UsbDevice,
        connection: UsbDeviceConnection
    ): IAudioEngine = UsbBulkEngine(context, usbDevice, connection)

    fun getEngineTypeFromString(value: String?): EngineType {
        return when (value) {
            "HI_RES"  -> EngineType.HI_RES
            "USB_DAC" -> EngineType.USB_DAC
            else      -> EngineType.NORMAL
        }
    }
}
