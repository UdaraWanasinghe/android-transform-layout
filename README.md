# android-transform-layout

An Android layout that supports simultaneous handling of scaling, rotation, translation and fling
gestures.

## Using

1. Import the library into your project.

```groovy
dependencies {
    implementation "com.aureusapps.android:transform-layout:1.0.0"
}
```

2. You can use the `TransformLayout` in your layout XML.

```xml

<com.aureusapps.android.transformlayout.TransformLayout android:id="@+id/transform_layout"
    android:layout_width="match_parent" android:layout_height="match_parent">

    <com.aureusapps.android.transformlayout.example.PainterLayout
        android:layout_width="match_parent" android:layout_height="match_parent" />

</com.aureusapps.android.transformlayout.TransformLayout>
```

3. You can use `TransformGestureDetector` in your custom view as follows.

```kotlin
class CustomView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    var isTransformEnabled = false
    val gestureDetector = TransformGestureDetector(context, gestureDetectorListener)

    private val gestureDetectorListener = object : TransformGestureDetectorListener {
        override fun onTransformStart(px: Float, py: Float, matrix: Matrix) {
        }

        override fun onTransformUpdate(px: Float, py: Float, oldMatrix: Matrix, newMatrix: Matrix) {
            invalidate()
        }

        override fun onTransformComplete(px: Float, py: Float, matrix: Matrix) {
        }

        override fun onSingleTap(px: Float, py: Float) {
        }
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        return isTransformEnabled
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isTransformEnabled) {
            return gestureDetector.onTouchEvent(event)
        }
        return false
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (!isTransformEnabled) {
            ev.transform(gestureDetector.touchMatrix)
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
        var result = false
        canvas.withMatrix(gestureDetector.drawMatrix) {
            result = super.drawChild(canvas, child, drawingTime)
        }
        return result
    }

}
```

## Styling

```xml

<style name="TransformLayoutStyle">
    <item name="transformEnabled">true</item>
    <item name="scaleEnabled">true</item>
    <item name="rotationEnabled">true</item>
    <item name="translationEnabled">true</item>
    <item name="flingEnabled">true</item>
</style>
```

## Appreciate my work!

If you find this library useful, please consider buying me a coffee.

<a href="https://www.buymeacoffee.com/udarawanasinghe" target="_blank"><img src="https://cdn.buymeacoffee.com/buttons/default-orange.png" alt="Buy Me A Coffee" height="41" width="174"></a>