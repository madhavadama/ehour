package net.rrm.ehour.ui.report.panel
package detail

import net.rrm.ehour.report.criteria.{ReportCriteria, AggregateBy, UserSelectedCriteria}
import net.rrm.ehour.report.reports.ReportData
import net.rrm.ehour.ui.common.panel.AbstractBasePanel
import net.rrm.ehour.ui.common.renderers.LocalizedResourceRenderer
import net.rrm.ehour.ui.common.report.{DetailedReportConfig, ReportConfig}
import net.rrm.ehour.ui.common.wicket.Event
import net.rrm.ehour.ui.report.cache.ReportCacheService
import net.rrm.ehour.ui.report.excel.DetailedReportExcel
import net.rrm.ehour.ui.report.trend.DetailedReportModel
import net.rrm.ehour.ui.report.{TreeReportData, TreeReportModel}
import org.apache.wicket.ajax.AjaxRequestTarget
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior
import org.apache.wicket.event.{Broadcast, IEvent}
import org.apache.wicket.markup.html.WebMarkupContainer
import org.apache.wicket.markup.html.form.DropDownChoice
import org.apache.wicket.markup.html.panel.Panel
import org.apache.wicket.model.PropertyModel
import org.apache.wicket.spring.injection.annot.SpringBean

object DetailedReportPanel {
  val AggregateToConfigMap = Map(AggregateBy.DAY -> DetailedReportConfig.DETAILED_REPORT_BY_DAY,
    AggregateBy.WEEK -> DetailedReportConfig.DETAILED_REPORT_BY_WEEK,
    AggregateBy.MONTH -> DetailedReportConfig.DETAILED_REPORT_BY_MONTH,
    AggregateBy.QUARTER -> DetailedReportConfig.DETAILED_REPORT_BY_QUARTER,
    AggregateBy.YEAR -> DetailedReportConfig.DETAILED_REPORT_BY_YEAR)
}

class DetailedReportPanel(id: String, report: DetailedReportModel) extends AbstractBasePanel[DetailedReportModel](id) {
  val Self = this

  setDefaultModel(report)
  setOutputMarkupId(true)

  @SpringBean
  var reportCacheService: ReportCacheService = _

  protected override def onBeforeRender() {
    val frame = new WebMarkupContainer("frame")
    addOrReplace(frame)

    val reportConfig = DetailedReportPanel.AggregateToConfigMap.getOrElse(report.getReportCriteria.getUserSelectedCriteria.getAggregateBy, DetailedReportConfig.DETAILED_REPORT_BY_DAY)

    val excel = new DetailedReportExcel(new PropertyModel[ReportCriteria](report, "reportCriteria"))

    frame.add(new TreeReportDataPanel("reportTable", report, reportConfig, excel) {
      protected override def createAdditionalOptions(id: String): WebMarkupContainer = new AggregateByDatePanel(id, report.getReportCriteria.getUserSelectedCriteria)
    })

    val reportData: ReportData = recalculateReportData()
    val cacheKey = storeReportData(reportData)

    val chartContainer = new DetailedReportChartContainer("chart", cacheKey)
    chartContainer.setVisible(!reportData.isEmpty)
    frame.add(chartContainer)

    super.onBeforeRender()
  }

  private def recalculateReportData():ReportData = {
    val reportModel = getDefaultModel.asInstanceOf[TreeReportModel]
    val treeReportData = reportModel.getReportData.asInstanceOf[TreeReportData]
    treeReportData.getRawReportData
  }

  private def storeReportData(data: ReportData) = reportCacheService.storeReportData(data)

  override def onEvent(event: IEvent[_]) = {
    event.getPayload match {
      case aggregateByChangedEvent: AggregateByChangedEvent =>
        val cacheKey = storeReportData(recalculateReportData())

        val reportDataEvent = new UpdateReportDataEvent(aggregateByChangedEvent.target, cacheKey, aggregateByChangedEvent.reportConfig)

        send(Self, Broadcast.BREADTH, reportDataEvent)
      case _ =>
    }
  }
}

import net.rrm.ehour.util._

class AggregateByDatePanel(id: String, criteria: UserSelectedCriteria) extends Panel(id) {
  val Self = this

  override def onInitialize() {
    super.onInitialize()

    val options = toJava(AggregateBy.values().toList)

    val aggregateSelect = new DropDownChoice[AggregateBy]("aggregateBy", new PropertyModel[AggregateBy](criteria, "aggregateBy"), options, new LocalizedResourceRenderer[AggregateBy]() {
      override protected def getResourceKey(o: AggregateBy): String = "userReport.report." + o.name.toLowerCase
    })

    aggregateSelect.add(new AjaxFormComponentUpdatingBehavior("change") {
      override def onUpdate(target: AjaxRequestTarget) {
        val aggregateBy = aggregateSelect.getModelObject

        send(Self.getPage, Broadcast.DEPTH, new AggregateByChangedEvent(target, DetailedReportPanel.AggregateToConfigMap(aggregateBy)))
      }
    })

    addOrReplace(aggregateSelect)
  }
}

case class AggregateByChangedEvent(t: AjaxRequestTarget, reportConfig: ReportConfig) extends Event(t)
