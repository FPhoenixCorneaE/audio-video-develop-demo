package com.fphoenixcorneae.camera2

import android.graphics.Bitmap
import android.graphics.Matrix

/**
 * 镜像
 */
fun Bitmap.mirror(): Bitmap = run {
    val matrix = Matrix()
    matrix.postScale(-1f, 1f)
    Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

/**
 * 旋转图片
 * @param degree 角度
 */
fun Bitmap.rotate(degree: Float): Bitmap = run {
    val matrix = Matrix()
    matrix.postRotate(degree)
    Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}