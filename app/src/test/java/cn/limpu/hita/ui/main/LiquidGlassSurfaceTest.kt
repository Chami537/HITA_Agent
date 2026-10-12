package cn.limpu.hita.ui.main

import androidx.compose.foundation.shape.CornerBasedShape
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidGlassSurfaceTest {

    @Test
    fun liquidGlassSurface_requiresCornerBasedShape() {
        val method = Class.forName("cn.limpu.hita.ui.main.MainActivityKt")
            .declaredMethods
            .single { it.name == "liquidGlassSurface" }

        assertTrue(
            "Backdrop lens effects crash at runtime for generic Shape implementations",
            method.parameterTypes.any { it == CornerBasedShape::class.java },
        )
    }
}
