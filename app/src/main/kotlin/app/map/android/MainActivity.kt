package app.map.android

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setPadding(32, 48, 32, 32)
        }
        content.addView(TextView(this).apply {
            text = "MAP"
            textSize = 32f
            contentDescription = "MAP home"
        })
        content.addView(TextView(this).apply {
            text = "Today"
            textSize = 22f
            setPadding(0, 32, 0, 12)
        })
        content.addView(TextView(this).apply {
            text = "Your day starts here."
            textSize = 16f
        })
        setContentView(content)
    }
}
