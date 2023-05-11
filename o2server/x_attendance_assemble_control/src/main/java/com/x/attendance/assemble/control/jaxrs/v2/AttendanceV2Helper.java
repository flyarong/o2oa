package com.x.attendance.assemble.control.jaxrs.v2;

import com.x.attendance.assemble.control.Business;
import com.x.attendance.entity.v2.AttendanceV2Group;
import com.x.base.core.container.EntityManagerContainer;
import com.x.base.core.project.config.Config;
import com.x.base.core.project.tools.DateTools;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellUtil;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

/**
 * 新版考勤 部分共用业务逻辑工具类
 * Created by fancyLou on 2023/4/24.
 * Copyright © 2023 O2. All rights reserved.
 */
public class AttendanceV2Helper {




    /**
     * 处理考勤组 考勤人员 将人员、组织全部换成人员DN
     * @param emc
     * @param business
     * @param groupId
     * @param participateList
     * @param unParticipateList
     * @return
     * @throws Exception
     */
    public static List<String> calTruePersonFromMixList(EntityManagerContainer emc, Business business, String groupId, List<String> participateList, List<String> unParticipateList) throws Exception {
        // 处理考勤组
        List<String> peopleList = new ArrayList<>();
        for (String p : participateList) {
            if (p.endsWith("@P")) {
                peopleList.add(p);
            } else if (p.endsWith("@I")) {
                String person = business.organization().person().getWithIdentity(p);
                peopleList.add(person);
            }else if (p.endsWith("@U")) { // 递归查询人员
                List<String> pList = business.organization().person().listWithUnitSubNested( p );
                peopleList.addAll(pList);
            } else {
                // LOGGER.info("错误的标识？ " + p);
            }
        }
        // 删除排除的人员
        if (unParticipateList != null && !unParticipateList.isEmpty()) {
            for (String p: unParticipateList) {
                peopleList.remove(p);
            }
        }
        // 去重复
        HashSet<String> peopleSet = new HashSet<>(peopleList);
        // 判断是否和其它考勤组内的成员冲突
        List<String> conflictPersonInOtherGroup = new ArrayList<>();
        List<AttendanceV2Group> groups = emc.listAll(AttendanceV2Group.class);
        if (groups != null && !groups.isEmpty()) {
            for (String person : peopleSet) {
                for (AttendanceV2Group oldG : groups) {
                    // 自己不用处理
                    if (oldG.getId().equals(groupId)) {
                        continue;
                    }
                    if (oldG.getTrueParticipantList().contains(person)) {
                        conflictPersonInOtherGroup.add(person);
                        break;
                    }
                }
            }
        }
        if (!conflictPersonInOtherGroup.isEmpty()) {
            throw new ExceptionParticipateConflict(conflictPersonInOtherGroup);
        }
//        if (LOGGER.isDebugEnabled()) {
//            LOGGER.debug("最终考勤组人员数：" + peopleSet.size());
//        }

        return new ArrayList<>(peopleSet);
    }


    /**
     * 是否特殊节假日
     * @param date yyyy-MM-dd
     * @param group
     * @return
     * @throws Exception
     */
    public static boolean isSpecialRestDay( String date, AttendanceV2Group group) throws Exception {
        boolean isRestDay = false;
        // 考勤配置 节假日工作日
        Date myDate = DateTools.parse(date, DateTools.format_yyyyMMdd);
        if (Config.workTime() != null && Config.workTime().inDefinedHoliday(myDate)) {
            isRestDay = true;
        }
        // 考勤组的无需打卡日
        if (group.getNoNeedCheckInDateList() != null && !group.getNoNeedCheckInDateList().isEmpty()) {
            for (String d : group.getNoNeedCheckInDateList()) { // 包含日期 ｜ 是否循环
                String[] dArray = d.split("\\|");
                if (dArray.length < 2) {
                    // 格式不正确
                    continue;
                }
                if (dArray[0].equals(date)) {
                    isRestDay = true;// 无需打卡就是休息日
                    break;
                }
                if (dArray[1].equals("week") && DateTools.dateIsInWeekCycle(DateTools.parse(dArray[0], DateTools.format_yyyyMMdd), DateTools.parse(date, DateTools.format_yyyyMMdd))) { // 每周
                    isRestDay = true;// 无需打卡就是休息日
                    break;
                }
                if (dArray[1].equals("twoWeek") && DateTools.dateIsInTwoWeekCycle(DateTools.parse(dArray[0], DateTools.format_yyyyMMdd), DateTools.parse(date, DateTools.format_yyyyMMdd))) { // 每周
                    isRestDay = true;// 无需打卡就是休息日
                    break;
                }
                if (dArray[1].equals("month") && DateTools.dateIsInMonthCycle(DateTools.parse(dArray[0], DateTools.format_yyyyMMdd), DateTools.parse(date, DateTools.format_yyyyMMdd))) { // 每周
                    isRestDay = true;// 无需打卡就是休息日
                    break;
                }
            }

        }
        return isRestDay;
    }



    /**
     * 是否特殊工作日 并返回工作日的班次id
     * @param date yyyy-MM-dd
     * @param group
     * @return
     * @throws Exception
     */
    public static String specialWorkDayShift(String date, AttendanceV2Group group) throws Exception {
        String shiftId = null;
        // 工作日
        Date myDate = DateTools.parse(date, DateTools.format_yyyyMMdd);
        if (Config.workTime() != null && Config.workTime().inDefinedWorkday(myDate)) {
            shiftId = group.getShiftId();
        }
        // 考勤组的必须打卡日
        if (group.getRequiredCheckInDateList() != null && !group.getRequiredCheckInDateList().isEmpty()) {
            for (String d : group.getRequiredCheckInDateList()) { // 包含日期 ｜ 班次id ｜ 是否循环
                String[] dArray = d.split("\\|");
                if (dArray.length < 3) {
                    // 格式不正确
                    continue;
                }
                if (dArray[0].equals(date)) {
                    shiftId = dArray[1];
                    break;
                }
                if (dArray[2].equals("week") && DateTools.dateIsInWeekCycle(DateTools.parse(dArray[0], DateTools.format_yyyyMMdd), DateTools.parse(date, DateTools.format_yyyyMMdd))) { // 每周
                    shiftId = dArray[1];
                    break;
                }
                if (dArray[2].equals("twoWeek") && DateTools.dateIsInTwoWeekCycle(DateTools.parse(dArray[0], DateTools.format_yyyyMMdd), DateTools.parse(date, DateTools.format_yyyyMMdd))) { // 每周
                    shiftId = dArray[1];
                    break;
                }
                if (dArray[2].equals("month") && DateTools.dateIsInMonthCycle(DateTools.parse(dArray[0], DateTools.format_yyyyMMdd), DateTools.parse(date, DateTools.format_yyyyMMdd))) { // 每周
                    shiftId = dArray[1];
                    break;
                }
            }
        }
        return shiftId;
    }

    public static void setExcelCellError(Row row, String error, int columnIndex) {
        Cell cell = CellUtil.getCell(row, columnIndex);
        cell.setCellValue(error);
    }

    public static String getExcelCellStringValue(Cell cell) {
        if (null != cell) {
            switch (cell.getCellType()) {
                case BLANK:
                    return "";
                case BOOLEAN:
                    return BooleanUtils.toString(cell.getBooleanCellValue(), "true", "false", "false");
                case ERROR:
                    return "";
                case FORMULA:
                    return "";
                case NUMERIC:
                    Double d = cell.getNumericCellValue();
                    Long l = d.longValue();
                    if (l.doubleValue() == d) {
                        return l.toString();
                    } else {
                        return d.toString();
                    }
                default:
                    return cell.getStringCellValue();
            }
        }
        return "";
    }
}
