package fi.hospitalos.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Same palette as mockups/glasses-scenes.html. */
object C {
  val Page = Color(0xFF0B0D12)
  val Panel = Color(0xFF12161D)
  val Line = Color(0xFF232A35)
  val Text = Color(0xFFE8EDF3)
  val Muted = Color(0xFF8A94A3)
  val Blue = Color(0xFF64D2FF)
  val Green = Color(0xFF5BE38F)
  val Amber = Color(0xFFFFCC4D)
  val Red = Color(0xFFFF5F57)
}

@Composable
fun HospitalTheme(content: @Composable () -> Unit) {
  MaterialTheme(
      colorScheme =
          darkColorScheme(
              primary = C.Blue,
              onPrimary = Color.Black,
              background = C.Page,
              surface = C.Panel,
              onSurface = C.Text,
              onBackground = C.Text,
              surfaceVariant = C.Panel,
              onSurfaceVariant = C.Muted,
              outline = C.Line,
              error = C.Red,
          ),
      content = content,
  )
}

/** Rounded panel, like the mockup's scene cards. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
  Column(
      modifier =
          modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(20.dp))
              .background(if (accent != null) accent.copy(alpha = 0.10f) else C.Panel)
              .border(1.dp, accent?.copy(alpha = 0.45f) ?: C.Line, RoundedCornerShape(20.dp))
              .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
      content = content,
  )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
  Text(text, color = C.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = modifier.padding(top = 18.dp, bottom = 6.dp, start = 4.dp))
}

@Composable
fun Title(text: String) = Text(text, color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)

@Composable
fun Body(text: String, color: Color = C.Text) = Text(text, color = color, fontSize = 15.sp)

/** Small status pill with a colored dot. */
@Composable
fun StatusPill(text: String, color: Color) {
  Row(
      modifier =
          Modifier.clip(RoundedCornerShape(999.dp))
              .background(color.copy(alpha = 0.14f))
              .padding(horizontal = 10.dp, vertical = 5.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Box(Modifier.size(8.dp).clip(CircleShape).background(color))
    Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
  }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, color: Color = C.Blue) {
  Button(
      onClick = onClick,
      enabled = enabled,
      modifier = modifier,
      shape = RoundedCornerShape(999.dp),
      colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.Black),
  ) {
    Text(text, fontWeight = FontWeight.SemiBold)
  }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
  OutlinedButton(
      onClick = onClick,
      enabled = enabled,
      modifier = modifier,
      shape = RoundedCornerShape(999.dp),
      border = BorderStroke(1.dp, C.Line),
  ) {
    Text(text, color = C.Text)
  }
}
