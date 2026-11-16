package co.daniel.mercado

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import co.daniel.mercado.ui.home.HomeScreen
import co.daniel.mercado.ui.theme.MercadoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MercadoTheme {
                HomeScreen()
            }
        }
    }
}
