package com.psychologist.financial.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psychologist.financial.domain.models.MonthlyMetrics
import java.math.BigDecimal

/**
 * Weekly breakdown component
 *
 * Displays revenue distribution across weeks of a month.
 * Shows bar chart visualization and weekly metrics.
 *
 * Layout:
 * ```
 * ┌─────────────────────────────────────┐
 * │ Distribuição por Semana              │
 * │ W1   W2   W3   W4   (Revenue bars)   │
 * │ ██   ███  ██   ███  (Height varies)  │
 * │ R$ 1100  R$ 1200  R$ 1000  R$ 1200  │
 * └─────────────────────────────────────┘
 * ```
 *
 * Features:
 * - Bar chart visualization
 * - Week labels with revenue amounts
 * - Color-coded by value (green high, orange medium, red low)
 * - Responsive height based on max value
 * - Weekly transaction counts
 * - Consistency assessment
 * - Material 3 styling
 *
 * Example:
 * ```kotlin
 * WeeklyBreakdown(
 *     metrics = monthlyMetrics
 * )
 * ```
 *
 * @param metrics MonthlyMetrics with weekly breakdown
 */
@Composable
fun WeeklyBreakdown(
    metrics: MonthlyMetrics
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Title
            Text(
                text = "Distribuição por Semana",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Bar chart
            if (metrics.weekCount > 0) {
                BarChart(metrics)
            } else {
                Text(
                    text = "Sem dados de receita neste mês",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }

            // Weekly details
            if (metrics.weekCount > 0) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    metrics.weeklyBreakdown.toSortedMap().forEach { (week, weeklyData) ->
                        WeeklyDetail(week, weeklyData)
                    }
                }

                // Consistency info
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Consistência",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = metrics.getConsistencyAssessment(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            metrics.consistencyScore >= 90 -> Color.Green
                            metrics.consistencyScore >= 75 -> Color(0xFF00AA00)
                            metrics.consistencyScore >= 50 -> Color(0xFFFFAA00)
                            else -> Color.Red
                        }
                    )
                }
            }
        }
    }
}

/**
 * Bar chart for weekly revenue
 *
 * @param metrics MonthlyMetrics with weekly breakdown
 */
@Composable
private fun BarChart(
    metrics: MonthlyMetrics
) {
    val maxRevenue = metrics.highestWeekRevenue
    val barHeight = 100.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(barHeight + 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        (1..metrics.weekCount).forEach { week ->
            val weeklyData = metrics.weeklyBreakdown[week]
            val revenue = weeklyData?.revenue ?: BigDecimal.ZERO
            val heightFraction = if (maxRevenue > BigDecimal.ZERO) {
                (revenue / maxRevenue).toFloat()
            } else {
                0f
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(barHeight),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(heightFraction)
                        .background(
                            color = getWeekBarColor(revenue, maxRevenue),
                            shape = MaterialTheme.shapes.small
                        )
                )

                // Week label
                Text(
                    text = "W$week",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/**
 * Weekly detail row
 *
 * @param week Week number
 * @param weeklyData Weekly metrics
 */
@Composable
private fun WeeklyDetail(
    week: Int,
    weeklyData: com.psychologist.financial.domain.models.WeeklyMetrics
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.05f),
                shape = MaterialTheme.shapes.small
            )
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "Semana $week - ${weeklyData.getFormattedDateRange()}",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${weeklyData.transactionCount} transações",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
        Text(
            text = weeklyData.getFormattedRevenue(),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * Get bar color based on revenue value
 *
 * Color gradient:
 * - Green: > 75% of max
 * - Yellow: 50-75% of max
 * - Orange: 25-50% of max
 * - Red: < 25% of max
 *
 * @param revenue Week revenue
 * @param maxRevenue Maximum revenue in month
 * @return Color for bar
 */
private fun getWeekBarColor(
    revenue: BigDecimal,
    maxRevenue: BigDecimal
): Color {
    if (maxRevenue == BigDecimal.ZERO) {
        return Color(0xFF6200EE)
    }

    val percentage = (revenue / maxRevenue * BigDecimal("100")).toFloat()

    return when {
        percentage >= 75f -> Color(0xFF00AA00)  // Green
        percentage >= 50f -> Color(0xFF00DD00)  // Light green
        percentage >= 25f -> Color(0xFFFFAA00)  // Orange
        else -> Color(0xFFDD4444)               // Red
    }
}

/**
 * Simplified weekly breakdown (list view)
 *
 * @param metrics MonthlyMetrics
 */
@Composable
fun WeeklyBreakdownList(
    metrics: MonthlyMetrics
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Receita por Semana",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (metrics.weekCount > 0) {
                metrics.weeklyBreakdown.toSortedMap().forEach { (week, weeklyData) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Semana $week",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = weeklyData.getFormattedDateRange(),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        Column(
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = weeklyData.getFormattedRevenue(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${weeklyData.transactionCount} trans.",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}
