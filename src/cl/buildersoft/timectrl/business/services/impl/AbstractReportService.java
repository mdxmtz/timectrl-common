package cl.buildersoft.timectrl.business.services.impl;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.jasperreports.engine.JRExporter;
import net.sf.jasperreports.engine.export.JRPdfExporter;
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter;
import cl.buildersoft.framework.database.BSBeanUtils;
import cl.buildersoft.framework.database.BSmySQL;
import cl.buildersoft.framework.exception.BSConfigurationException;
import cl.buildersoft.framework.exception.BSDataBaseException;
import cl.buildersoft.framework.exception.BSProgrammerException;
import cl.buildersoft.framework.exception.BSSystemException;
import cl.buildersoft.framework.util.BSDataUtils;
import cl.buildersoft.framework.util.BSDateTimeUtil;
import cl.buildersoft.framework.util.BSUtils;
import cl.buildersoft.framework.util.BSWeb;
import cl.buildersoft.timectrl.business.beans.Employee;
import cl.buildersoft.timectrl.business.beans.IdRut;
import cl.buildersoft.timectrl.business.beans.Report;
import cl.buildersoft.timectrl.business.beans.ReportParameterBean;
import cl.buildersoft.timectrl.business.beans.ReportPropertyBean;
import cl.buildersoft.timectrl.business.beans.ReportPropertyType;
import cl.buildersoft.timectrl.business.beans.ReportType;
import cl.buildersoft.timectrl.business.services.ReportService;

@SuppressWarnings("unchecked")
public abstract class AbstractReportService {
	private static final Logger LOG = Logger.getLogger(AbstractReportService.class.getName());
	protected static final String NOT_FOUND = "' not found.";

	protected abstract Boolean getPropertyValue(String key, String value);

	protected abstract String parseCustomVariable(String key);

	public List<ReportParameterBean> loadParameter(Connection conn, Long idReport) {
		List<ReportParameterBean> out = new ArrayList<ReportParameterBean>();
		BSmySQL mysql = new BSmySQL();

		ResultSet rs = mysql.callSingleSP(conn, "pListReportParameter", idReport);

		try {
			ReportParameterBean rpb = null;
			while (rs.next()) {
				rpb = new ReportParameterBean();
				rpb.setId(rs.getLong("cId"));
				rpb.setReport(rs.getLong("cReport"));
				rpb.setName(rs.getString("cName"));
				rpb.setLabel(rs.getString("cLabel"));
				rpb.setOrder(rs.getInt("cOrder"));
				rpb.setTypeId(rs.getLong("cTypeId"));
				rpb.setTypeKey(rs.getString("cTypeKey"));
				rpb.setTypeName(rs.getString("cTypeName"));
				rpb.setHtmlFile(rs.getString("cTypeHTMLFile"));
				rpb.setTypeSource(rs.getString("cTypeSource"));
				rpb.setJavaType(rs.getString("cJavaType"));
				out.add(rpb);
			}
		} catch (SQLException e) {
			e.printStackTrace();
			throw new BSDataBaseException(e);
		} finally {
			mysql.closeSQL(rs);
		}

		return out;
	}

	public List<ReportPropertyBean> loadReportProperties(Connection conn, Long idReport) {
		/**
		 * <code>
		 * 
		BSBeanUtils bu = new BSBeanUtils();

		return (List<ReportPropertyBean>) bu.list(conn, new ReportPropertyBean(), "cReport=?", idReport);

		 * </code>
		 */
		BSmySQL mysql = new BSmySQL();
		List<ReportPropertyBean> out = new ArrayList<ReportPropertyBean>();

		ResultSet rs = mysql.callSingleSP(conn, "pListReportPropertyByReportId", idReport);

		try {
			ReportPropertyBean reportProperty = null;
			while (rs.next()) {
				reportProperty = new ReportPropertyBean();

				reportProperty.setPropertyId(rs.getLong("cPropertyId"));
				reportProperty.setPropertyType(rs.getLong("cPropertyType"));
				reportProperty.setPropertyReport(rs.getLong("cPropertyReport"));
				reportProperty.setPropertyValue(rs.getString("cPropertyValue"));
				reportProperty.setPropertyTypeId(rs.getLong("cPropertyTypeId"));
				reportProperty.setPropertyTypeKey(rs.getString("cPropertyTypeKey"));
				reportProperty.setPropertyTypeName(rs.getString("PropertyTypeName"));

				out.add(reportProperty);
			}
		} catch (SQLException e) {
			throw new BSDataBaseException(e);
		}

		return out;
	}

	/**
	 * <code>
	public List<ReportPropertyType> loadOutValues(Connection conn, List<ReportPropertyBean> outParams) {
		BSBeanUtils bu = new BSBeanUtils();
		List<ReportPropertyType> out = new ArrayList<ReportPropertyType>();

		ReportPropertyType reportPropertyType = null;
		for (ReportPropertyBean current : outParams) {
			reportPropertyType = new ReportPropertyType();
			if (!bu.search(conn, reportPropertyType, "cParam=?", current.getId())) {
				throw new BSConfigurationException("Out parameter '" + current.getId() + "' not found");
			}
			out.add(reportPropertyType);
		}

		return out;
	}
</code>
	 */
	public void fillParameters(List<ReportParameterBean> reportInParamList, List<String> valueList) {
		// String paramName = null;
		// String value = null;
		Integer index = 0;

		for (ReportParameterBean currentParam : reportInParamList) {
			currentParam.setValue(valueList.get(index++));
		}

	}

	protected ReportPropertyType getPropertyType(Connection conn, Long id) {
		BSBeanUtils bu = new BSBeanUtils();
		ReportPropertyType reportPropertyType = new ReportPropertyType();
		reportPropertyType.setId(id);

		if (!bu.search(conn, reportPropertyType)) {
			throw new BSProgrammerException("Can't find Property Type width id='" + id + "'");
		}
		return reportPropertyType;
	}

	protected String fixPath(String folderName) {
		String out = null;
		if (!folderName.endsWith(File.separator)) {
			out = folderName + File.separator;
		} else {
			out = folderName;
		}
		return out;
	}

	protected Object getParameterType(Connection conn, String javaType, BSBeanUtils bu, String value) {
		Object out = null;

		if ("INTEGER".equalsIgnoreCase(javaType)) {
			out = Integer.parseInt(value);
		} else if ("LONG".equalsIgnoreCase(javaType)) {
			out = Long.parseLong(value);
		} else if ("DATE".equalsIgnoreCase(javaType)) {
			String dateTimeFormat = BSDateTimeUtil.getFormatDate(conn);
			Calendar calendar = BSDateTimeUtil.string2Calendar(value, dateTimeFormat);
			out = BSDateTimeUtil.calendar2Date(calendar);
		} else if ("STRING".equalsIgnoreCase(javaType)) {
			out = value;
		}
		return out;
	}

	protected void readProperties(Connection conn, List<ReportPropertyBean> reportPropertyList) {
		ReportPropertyType rpt = null;
		for (ReportPropertyBean reportProperty : reportPropertyList) {
			rpt = getPropertyType(conn, reportProperty.getPropertyType());
			getPropertyValue(rpt.getKey(), reportProperty.getPropertyValue());
		}
	}

	@SuppressWarnings("deprecation")
	protected JRExporter getExporter(String format) {
		JRExporter out = null;

		if ("pdf".equalsIgnoreCase(format.toLowerCase())) {
			out = new JRPdfExporter();
		} else {
			Boolean ifExcel = "excel".equalsIgnoreCase(format.toLowerCase()) || "xlsx".equalsIgnoreCase(format.toLowerCase())
					|| "xls".equalsIgnoreCase(format.toLowerCase());
			if (ifExcel) {
				out = new JRXlsxExporter();
			}
		}

		/**
		 * <code>
		switch (format.toLowerCase()) {
		case "pdf":
			out = new JRPdfExporter();
			break;
		case "excel":
		case "xlsx":
		case "xls":
			out = new JRXlsxExporter();
			break;
		default:
			throw new BSConfigurationException("Format '" + format + "' unknown");
		}
</code>
		 */
		return out;
	}

	protected String parsePropertes(String value, String[] keyValues) {
		Calendar calendar = Calendar.getInstance();
		String out = value.replaceAll("\\x7BDate\\x7D", BSDateTimeUtil.calendar2String(calendar, "yyyy-MM-dd"));
		out = out.replaceAll("\\x7BMonth\\x7D", BSDateTimeUtil.calendar2String(calendar, "MM"));
		out = out.replaceAll("\\x7BYear\\x7D", BSDateTimeUtil.calendar2String(calendar, "yyyy"));
		out = out.replaceAll("\\x7BDay\\x7D", BSDateTimeUtil.calendar2String(calendar, "dd"));
		out = out.replaceAll("\\x7BRandom\\x7D", BSWeb.randomString());

		out = replaceEnviromentValues(out);
		// out = out.replaceAll("\\x7BBS_PATH\\x7D",
		// System.getenv("BS_PATH").replaceAll("\\x5C", "\\\\"));

		for (String keyValue : keyValues) {
			String newValue = null;

			newValue = parseCustomVariable(keyValue);
			out = out.replaceAll("\\x7B" + keyValue + "\\x7D", newValue == null ? "" : newValue);

		}

		return out;
	}

	private String replaceEnviromentValues(String out) {
		Map<String, String> env = System.getenv();
		for (String envName : env.keySet()) {
			out = out.replace("{" + envName + "}", env.get(envName));
			// System.out.format("%s=%s%n", envName, env.get(envName));
		}

		return out;
	}

	protected void createPathIfNotExists(String outputPath) {
		File folder = new File(outputPath);
		if (!folder.exists()) {
			if (!folder.mkdirs()) {
				throw new BSSystemException("Can't create folder [" + outputPath + "]");
			}
		}

	}

	public ReportService getInstance(Connection conn, Report report) {
		ReportType reportType = new ReportType();
		reportType.setId(report.getType());
		BSBeanUtils bu = new BSBeanUtils();
		if (!bu.search(conn, reportType)) {
			throw new BSProgrammerException("Report type '" + report.getType() + NOT_FOUND);
		}
		return getInstance(reportType);
	}

	public ReportService getInstance(ReportType reportType) {
		ReportService instance = null;
		try {
			Class<ReportService> javaClass = (Class<ReportService>) Class.forName(reportType.getJavaClass());
			instance = (ReportService) javaClass.newInstance();
		} catch (Exception e) {
			e.printStackTrace();
			throw new BSProgrammerException(e);
		}
		return instance;
	}

	protected ReportParameterBean getEmployeeParameter(List<ReportParameterBean> reportInputParameterList) {
		ReportParameterBean out = null;
		for (ReportParameterBean reportInputParameter : reportInputParameterList) {
			if ("EMPLOYEE_LIST".equalsIgnoreCase(reportInputParameter.getTypeKey())) {
				out = reportInputParameter;
				break;
			}
		}
		return out;
	}

	public List<IdRut> getEmployeeList(Connection conn, String idEmploye) {
		List<IdRut> out = new ArrayList<IdRut>();
		IdRut data = null;
		Boolean allEmployee = "0".equals(idEmploye);
		String sql = null;
		List<Object> param = null;

		String[] employeeIdArray = idEmploye.split("[,]");

		sql = "SELECT tEmployee.cId AS UserId, tEmployee.cKey AS cKey, tEmployee.cRut AS SSN, tEmployee.cName AS Name, tArea.cCostCenter AS DEFAULTDEPTID, tEmployee.cUsername AS cUsername, cMail ";
		sql += "FROM tEmployee ";
		sql += "LEFT JOIN tArea ON tEmployee.cArea = tArea.cId ";
		sql += allEmployee ? "" : "WHERE tEmployee.cId IN (" + BSUtils.getCommas(employeeIdArray)
				+ ") AND tEmployee.cEnabled=TRUE";
		// sql += getOrderSQL(conn);

		if (!allEmployee) {
			param = new ArrayList<Object>();
			for (String employeeId : employeeIdArray) {
				param.add(employeeId);
			}
		}

		LOG.log(Level.FINE, sql);

		BSDataUtils du = new BSDataUtils();
		ResultSet rs = du.queryResultSet(conn, sql, param);
		try {
			while (rs.next()) {
				data = new IdRut();
				data.setId(rs.getInt("UserId"));
				data.setKey(rs.getString("cKey"));
				data.setRut(rs.getString("SSN"));
				data.setName(rs.getString("Name"));
				data.setCostCenter(rs.getString("DEFAULTDEPTID"));
				data.setUsername(rs.getString("cUsername"));
				data.setMail(rs.getString("cMail"));

				// data.add("" + rs.getInt(1));
				// data.add(rs.getString(2));

				out.add(data);
			}
		} catch (Exception e) {
			e.printStackTrace();
			throw new BSConfigurationException(e);
		}
		return out;
	}

	protected void updateEmployeeId(List<ReportParameterBean> reportInputParameterList, Integer id) {
		for (ReportParameterBean reportInputParameter : reportInputParameterList) {
			if ("EMPLOYEE_LIST".equalsIgnoreCase(reportInputParameter.getTypeKey())) {
				reportInputParameter.setValue(id.toString());
				break;
			}
		}
	}

	protected void processEmployeeParameter(Connection conn, List<ReportParameterBean> reportParameterList) {
		for (ReportParameterBean parameter : reportParameterList) {
			LOG.log(Level.FINE, parameter.toString());
			if (parameter.getTypeKey().equalsIgnoreCase("EMPLOYEE_LIST") && parameter.getValue().equals("0")) {
				replaceZeroWithAllIds(conn, parameter);
				parameter.setJavaType("STRING");
			}
		}

	}

	private void replaceZeroWithAllIds(Connection conn, ReportParameterBean parameter) {
		BSBeanUtils bu = new BSBeanUtils();
		String newValue = "";

		List<Employee> employeeList = (List<Employee>) bu.listAll(conn, new Employee());
		String[] idsArray = new String[employeeList.size()];
		Integer index = 0;
		for (Employee employee : employeeList) {
			idsArray[index++] = employee.getId().toString();
		}
		newValue = BSUtils.unSplitString(idsArray, ",");
		parameter.setValue(newValue);
	}

	public ReportParameterBean getReportParameter(List<ReportParameterBean> parameterList, String parameterName) {
		ReportParameterBean out = null;
		for (ReportParameterBean parameter : parameterList) {
			if (parameter.getTypeKey().equalsIgnoreCase(parameterName)) {
				out = parameter;
				break;
			}
		}
		return out;
	}

	public ReportPropertyBean getReportProperty(List<ReportPropertyBean> propertiesList, String propertyName) {
		ReportPropertyBean out = null;
		for (ReportPropertyBean property : propertiesList) {
			if (property.getPropertyTypeKey().equalsIgnoreCase(propertyName)) {
				out = property;
				break;
			}
		}
		return out;
	}
}
