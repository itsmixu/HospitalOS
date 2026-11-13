package fi.hospitalos.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.getValue
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
  // Accent color fades when a panel changes state; size changes animate instead of jumping.
  val bg by animateColorAsState(if (accent != null) accent.copy(alpha = 0.10f) else C.Panel, tween(300), label = "panelBg")
  val edge by animateColorAsState(accent?.copy(alpha = 0.45f) ?: C.Line, tween(300), label = "panelEdge")
  Column(
      modifier =
          modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(20.dp))
              .background(bg)
              .border(1.dp, edge, RoundedCornerShape(20.dp))
              .animateContentSize(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow))
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
  // Status changes (Sent → Seen → Accepted) fade color and crossfade the label.
  val c by animateColorAsState(color, tween(350), label = "pill")
  Row(
      modifier =
          Modifier.clip(RoundedCornerShape(999.dp))
              .background(c.copy(alpha = 0.14f))
              .animateContentSize()
              .padding(horizontal = 10.dp, vertical = 5.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Box(Modifier.size(8.dp).clip(CircleShape).background(c))
    AnimatedContent(text, transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) }, label = "pillText") { t ->
      Text(t, color = c, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
  }
}

/** Standard enter/exit for sections that appear and disappear (requests, drafts). */
val SectionEnter = fadeIn(tween(250)) + expandVertically(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow))
val SectionExit = fadeOut(tween(150)) + shrinkVertically(tween(250))

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
