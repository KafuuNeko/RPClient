package me.kafuuneko.rpclient.feature.imagecrop.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageCropTransformTest {
    @Test
    fun landscapeImageAtMinimumZoomUsesFullHeight() {
        val selection = ImageCropTransform(sourceAspectRatio = 2f).toSelection()

        assertEquals(0.5f, selection.centerX, EPSILON)
        assertEquals(0.5f, selection.centerY, EPSILON)
        assertEquals(1f, selection.sizeFractionOfShortEdge, EPSILON)
        assertEquals(0, selection.rotationDegrees)
        assertFalse(selection.isFlippedHorizontal)
    }

    @Test
    fun panIsClampedToImageBounds() {
        val selection = ImageCropTransform(sourceAspectRatio = 2f)
            .update(panX = 10f, panY = 10f, zoomChange = 1f)
            .toSelection()

        assertEquals(0.25f, selection.centerX, EPSILON)
        assertEquals(0.5f, selection.centerY, EPSILON)
    }

    @Test
    fun zoomKeepsSelectedCenterWhileReducingCropSize() {
        val panned = ImageCropTransform(sourceAspectRatio = 1f)
            .update(panX = 0.2f, panY = -0.1f, zoomChange = 2f)
        val before = panned.toSelection()
        val after = panned.update(panX = 0f, panY = 0f, zoomChange = 2f).toSelection()

        assertEquals(before.centerX, after.centerX, EPSILON)
        assertEquals(before.centerY, after.centerY, EPSILON)
        assertEquals(0.25f, after.sizeFractionOfShortEdge, EPSILON)
    }

    @Test
    fun rotationInvertsEffectiveAspectRatioAndCycles360Degrees() {
        val initial = ImageCropTransform(sourceAspectRatio = 2f)
        assertEquals(2f, initial.effectiveAspectRatio, EPSILON)
        assertFalse(initial.isRotated90)

        val rotated90 = initial.rotateRight()
        assertEquals(90, rotated90.rotationDegrees)
        assertTrue(rotated90.isRotated90)
        assertEquals(0.5f, rotated90.effectiveAspectRatio, EPSILON)

        val rotated180 = rotated90.rotateRight()
        assertEquals(180, rotated180.rotationDegrees)
        assertFalse(rotated180.isRotated90)
        assertEquals(2f, rotated180.effectiveAspectRatio, EPSILON)

        val rotated270 = rotated180.rotateRight()
        assertEquals(270, rotated270.rotationDegrees)
        assertTrue(rotated270.isRotated90)
        assertEquals(0.5f, rotated270.effectiveAspectRatio, EPSILON)

        val rotated360 = rotated270.rotateRight()
        assertEquals(0, rotated360.rotationDegrees)
        assertFalse(rotated360.isRotated90)
    }

    @Test
    fun flipHorizontalTogglesStateAndInvertsOffsetX() {
        val initial = ImageCropTransform(sourceAspectRatio = 2f)
            .update(panX = 0.3f, panY = 0f, zoomChange = 2f)
        val flipped = initial.flipHorizontal()

        assertTrue(flipped.isFlippedHorizontal)
        assertEquals(-initial.offsetX, flipped.offsetX, EPSILON)

        val selection = flipped.toSelection()
        assertTrue(selection.isFlippedHorizontal)
    }

    private companion object {
        const val EPSILON = 0.0001f
    }
}

