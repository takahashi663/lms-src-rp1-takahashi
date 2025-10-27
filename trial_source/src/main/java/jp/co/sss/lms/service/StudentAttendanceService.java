package jp.co.sss.lms.service;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jp.co.sss.lms.dto.AttendanceManagementDto;
import jp.co.sss.lms.dto.LoginUserDto;
import jp.co.sss.lms.entity.TStudentAttendance;
import jp.co.sss.lms.enums.AttendanceStatusEnum;
import jp.co.sss.lms.form.AttendanceForm;
import jp.co.sss.lms.form.DailyAttendanceForm;
import jp.co.sss.lms.mapper.TStudentAttendanceMapper;
import jp.co.sss.lms.util.AttendanceUtil;
import jp.co.sss.lms.util.Constants;
import jp.co.sss.lms.util.DateUtil;
import jp.co.sss.lms.util.LoginUserUtil;
import jp.co.sss.lms.util.MessageUtil;
import jp.co.sss.lms.util.TrainingTime;

/**
 * 勤怠情報（受講生入力）サービス
 * 
 * @author 東京ITスクール
 */
@Service
public class StudentAttendanceService {

	@Autowired
	private DateUtil dateUtil;
	@Autowired
	private AttendanceUtil attendanceUtil;
	@Autowired
	private MessageUtil messageUtil;
	@Autowired
	private LoginUserUtil loginUserUtil;
	@Autowired
	private LoginUserDto loginUserDto;
	@Autowired
	private TStudentAttendanceMapper tStudentAttendanceMapper;

	/**
	 * 勤怠一覧情報取得
	 * 
	 * @param courseId
	 * @param lmsUserId
	 * @return 勤怠管理画面用DTOリスト
	 */
	public List<AttendanceManagementDto> getAttendanceManagement(Integer courseId,
			Integer lmsUserId) {

		// 勤怠管理リストの取得
		List<AttendanceManagementDto> attendanceManagementDtoList = tStudentAttendanceMapper
				.getAttendanceManagement(courseId, lmsUserId, Constants.DB_FLG_FALSE);
		for (AttendanceManagementDto dto : attendanceManagementDtoList) {
			// 中抜け時間を設定
			if (dto.getBlankTime() != null) {
				TrainingTime blankTime = attendanceUtil.calcBlankTime(dto.getBlankTime());
				dto.setBlankTimeValue(String.valueOf(blankTime));
			}
			// 遅刻早退区分判定
			AttendanceStatusEnum statusEnum = AttendanceStatusEnum.getEnum(dto.getStatus());
			if (statusEnum != null) {
				dto.setStatusDispName(statusEnum.name);
			}

		}

		return attendanceManagementDtoList;
	}

	/**
	 * 出退勤更新前のチェック
	 * 
	 * @param attendanceType
	 * @return エラーメッセージ
	 */
	public String punchCheck(Short attendanceType) {
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 権限チェック
		if (!loginUserUtil.isStudent()) {
			return messageUtil.getMessage(Constants.VALID_KEY_AUTHORIZATION);
		}
		// 研修日チェック
		if (!attendanceUtil.isWorkDay(loginUserDto.getCourseId(), trainingDate)) {
			return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_NOTWORKDAY);
		}
		// 登録情報チェック
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		switch (attendanceType) {
		case Constants.CODE_VAL_ATWORK:
			if (tStudentAttendance != null
					&& !tStudentAttendance.getTrainingStartTime().equals("")) {
				// 本日の勤怠情報は既に入力されています。直接編集してください。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHALREADYEXISTS);
			}
			break;
		case Constants.CODE_VAL_LEAVING:
			if (tStudentAttendance == null
					|| tStudentAttendance.getTrainingStartTime().equals("")) {
				// 出勤情報がないため退勤情報を入力出来ません。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHINEMPTY);
			}
			if (!tStudentAttendance.getTrainingEndTime().equals("")) {
				// 本日の勤怠情報は既に入力されています。直接編集してください。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHALREADYEXISTS);
			}
			TrainingTime trainingStartTime = new TrainingTime(
					tStudentAttendance.getTrainingStartTime());
			TrainingTime trainingEndTime = new TrainingTime();
			if (trainingStartTime.compareTo(trainingEndTime) > 0) {
				// 退勤時刻は出勤時刻より後でなければいけません。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_TRAININGTIMERANGE);
			}
			break;
		}
		return null;
	}

	/**
	 * 出勤ボタン処理
	 * 
	 * @return 完了メッセージ
	 */
	public String setPunchIn() {
		// 当日日付
		Date date = new Date();
		// 本日の研修日
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 現在の研修時刻
		TrainingTime trainingStartTime = new TrainingTime();
		// 遅刻早退ステータス
		AttendanceStatusEnum attendanceStatusEnum = attendanceUtil.getStatus(trainingStartTime,
				null);
		// 研修日の勤怠情報取得
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		if (tStudentAttendance == null) {
			// 登録処理
			tStudentAttendance = new TStudentAttendance();
			tStudentAttendance.setLmsUserId(loginUserDto.getLmsUserId());
			tStudentAttendance.setTrainingDate(trainingDate);
			tStudentAttendance.setTrainingStartTime(trainingStartTime.toString());
			tStudentAttendance.setTrainingEndTime("");
			tStudentAttendance.setStatus(attendanceStatusEnum.code);
			tStudentAttendance.setNote("");
			tStudentAttendance.setAccountId(loginUserDto.getAccountId());
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			tStudentAttendance.setFirstCreateUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setFirstCreateDate(date);
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			tStudentAttendance.setBlankTime(null);
			tStudentAttendanceMapper.insert(tStudentAttendance);
		} else {
			// 更新処理
			tStudentAttendance.setTrainingStartTime(trainingStartTime.toString());
			tStudentAttendance.setStatus(attendanceStatusEnum.code);
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			tStudentAttendanceMapper.update(tStudentAttendance);
		}
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 * 退勤ボタン処理
	 * 
	 * @return 完了メッセージ
	 */
	public String setPunchOut() {
		// 当日日付
		Date date = new Date();
		// 本日の研修日
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 研修日の勤怠情報取得
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		// 出退勤時刻
		TrainingTime trainingStartTime = new TrainingTime(
				tStudentAttendance.getTrainingStartTime());
		TrainingTime trainingEndTime = new TrainingTime();
		// 遅刻早退ステータス
		AttendanceStatusEnum attendanceStatusEnum = attendanceUtil.getStatus(trainingStartTime,
				trainingEndTime);
		// 更新処理
		tStudentAttendance.setTrainingEndTime(trainingEndTime.toString());
		tStudentAttendance.setStatus(attendanceStatusEnum.code);
		tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
		tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
		tStudentAttendance.setLastModifiedDate(date);
		tStudentAttendanceMapper.update(tStudentAttendance);
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 * 勤怠フォームへ設定
	 * 
	 * @param attendanceManagementDtoList
	 * @return 勤怠編集フォーム
	 */
	public AttendanceForm setAttendanceForm(
			List<AttendanceManagementDto> attendanceManagementDtoList) {

		AttendanceForm attendanceForm = new AttendanceForm();
		attendanceForm.setAttendanceList(new ArrayList<DailyAttendanceForm>());
		attendanceForm.setLmsUserId(loginUserDto.getLmsUserId());
		attendanceForm.setUserName(loginUserDto.getUserName());
		attendanceForm.setLeaveFlg(loginUserDto.getLeaveFlg());
		attendanceForm.setBlankTimes(attendanceUtil.setBlankTime());

		// 途中退校している場合のみ設定
		if (loginUserDto.getLeaveDate() != null) {
			attendanceForm
					.setLeaveDate(dateUtil.dateToString(loginUserDto.getLeaveDate(), "yyyy-MM-dd"));
			attendanceForm.setDispLeaveDate(
					dateUtil.dateToString(loginUserDto.getLeaveDate(), "yyyy年M月d日"));
		}

		// 勤怠管理リストの件数分、日次の勤怠フォームに移し替え
		for (AttendanceManagementDto attendanceManagementDto : attendanceManagementDtoList) {
			DailyAttendanceForm dailyAttendanceForm = new DailyAttendanceForm();
			dailyAttendanceForm
					.setStudentAttendanceId(attendanceManagementDto.getStudentAttendanceId());
			dailyAttendanceForm
					.setTrainingDate(dateUtil.toString(attendanceManagementDto.getTrainingDate()));

			dailyAttendanceForm.setTrainingEndTime(attendanceManagementDto.getTrainingEndTime());
			if (attendanceManagementDto.getBlankTime() != null) {
				dailyAttendanceForm.setBlankTime(attendanceManagementDto.getBlankTime());
				dailyAttendanceForm.setBlankTimeValue(String.valueOf(
						attendanceUtil.calcBlankTime(attendanceManagementDto.getBlankTime())));
			}

			dailyAttendanceForm.setDispTrainingDate(dateUtil
					.dateToString(attendanceManagementDto.getTrainingDate(), "yyyy年M月d日(E)"));
			dailyAttendanceForm.setStatusDispName(attendanceManagementDto.getStatusDispName());

			attendanceForm.getAttendanceList().add(dailyAttendanceForm);
		}

		attendanceForm.setStartHourList(createTrainingStartTimeMapHour());
		attendanceForm.setStartMinuteList(createTrainingStartTimeMapMinute());
		attendanceForm.setEndHourList(createTrainingEndTimeMapHour());
		attendanceForm.setEndMinuteList(createTrainingEndTimeMapMinute());

		return attendanceForm;

	}

	//出勤時間リストを作成
	//出勤:時間
	private LinkedHashMap<Integer, String> createTrainingStartTimeMapHour() {
		LinkedHashMap<Integer, String> map = new LinkedHashMap<>();
		map.put(null, "");
		for (int h = 0; h <= 24; h++) {

			map.put(h, String.format("%02d", h));

		}
		return map;
	}

	//出勤:分
	private LinkedHashMap<Integer, String> createTrainingStartTimeMapMinute() {
		LinkedHashMap<Integer, String> map = new LinkedHashMap<>();
		map.put(null, "");
		for (int m = 0; m < 60; m += 1) {

			map.put(m, String.format("%02d", m));
		}
		return map;
	}

	//時間
	private LinkedHashMap<Integer, String> createTrainingEndTimeMapHour() {
		LinkedHashMap<Integer, String> map = new LinkedHashMap<>();
		map.put(null, "");
		for (int h = 0; h <= 24; h++) {

			map.put(h, String.format("%02d", h));

		}
		return map;
	}

	//分
	private LinkedHashMap<Integer, String> createTrainingEndTimeMapMinute() {
		LinkedHashMap<Integer, String> map = new LinkedHashMap<>();
		map.put(null, "");
		for (int m = 0; m < 60; m += 1) {

			map.put(m, String.format("%02d", m));
		}
		return map;
	}

	/**
	 * 勤怠登録・更新処理
	 * 
	 * @param attendanceForm
	 * @return 完了メッセージ
	 * @throws ParseException
	 */
	public String update(AttendanceForm attendanceForm) throws ParseException {

		Integer lmsUserId = loginUserUtil.isStudent() ? loginUserDto.getLmsUserId()
				: attendanceForm.getLmsUserId();

		// 現在の勤怠情報（受講生入力）リストを取得
		List<TStudentAttendance> tStudentAttendanceList = tStudentAttendanceMapper
				.findByLmsUserId(lmsUserId, Constants.DB_FLG_FALSE);

		// 入力された情報を更新用のエンティティに移し替え
		Date date = new Date();
		for (DailyAttendanceForm dailyAttendanceForm : attendanceForm.getAttendanceList()) {

			// 更新用エンティティ作成
			TStudentAttendance tStudentAttendance = new TStudentAttendance();
			// 日次勤怠フォームから更新用のエンティティにコピー
			BeanUtils.copyProperties(dailyAttendanceForm, tStudentAttendance);
			// 研修日付
			tStudentAttendance
					.setTrainingDate(dateUtil.parse(dailyAttendanceForm.getTrainingDate()));
			// 現在の勤怠情報リストのうち、研修日が同じものを更新用エンティティで上書き
			for (TStudentAttendance entity : tStudentAttendanceList) {
				if (entity.getTrainingDate().equals(tStudentAttendance.getTrainingDate())) {
					tStudentAttendance = entity;
					break;
				}
			}
			tStudentAttendance.setLmsUserId(lmsUserId);
			tStudentAttendance.setAccountId(loginUserDto.getAccountId());
			// 出勤時刻整形
			TrainingTime trainingStartTime = null;
			trainingStartTime = new TrainingTime(dailyAttendanceForm.getTrainingStartTime());
			tStudentAttendance.setTrainingStartTime(trainingStartTime.getFormattedString());
			// 退勤時刻整形
			TrainingTime trainingEndTime = null;
			trainingEndTime = new TrainingTime(dailyAttendanceForm.getTrainingEndTime());
			tStudentAttendance.setTrainingEndTime(trainingEndTime.getFormattedString());
			// 中抜け時間
			tStudentAttendance.setBlankTime(dailyAttendanceForm.getBlankTime());
			// 遅刻早退ステータス
			if ((trainingStartTime != null || trainingEndTime != null)
					&& !dailyAttendanceForm.getStatusDispName().equals("欠席")) {
				AttendanceStatusEnum attendanceStatusEnum = attendanceUtil
						.getStatus(trainingStartTime, trainingEndTime);
				tStudentAttendance.setStatus(attendanceStatusEnum.code);
			}
			// 備考
			tStudentAttendance.setNote(dailyAttendanceForm.getNote());
			// 更新者と更新日時
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			// 削除フラグ
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			// 登録用Listへ追加
			tStudentAttendanceList.add(tStudentAttendance);
		}
		// 登録・更新処理
		for (TStudentAttendance tStudentAttendance : tStudentAttendanceList) {
			if (tStudentAttendance.getStudentAttendanceId() == null) {
				tStudentAttendance.setFirstCreateUser(loginUserDto.getLmsUserId());
				tStudentAttendance.setFirstCreateDate(date);
				tStudentAttendanceMapper.insert(tStudentAttendance);
			} else {
				tStudentAttendanceMapper.update(tStudentAttendance);
			}
		}
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);

	}

	//勤怠情報（受講生入力）取得（LMSユーザーID＆日付）してカウント
	public boolean notEnterCount() {
		Date trainingDate = new Date();
		Integer result = tStudentAttendanceMapper.notEnterCount(loginUserDto.getLmsUserId(), Constants.DB_FLG_FALSE,
				trainingDate);
		return result != null && result > 0;

	}

	public DailyAttendanceForm combineTrainingTimes(DailyAttendanceForm form) {
		// 出勤時間の結合
		String startHour = form.getTrainingStartTimeHour();
		String startMinute = form.getTrainingStartTimeMinute();
		if (startHour != null && !startHour.isEmpty() && startMinute != null && !startMinute.isEmpty()) {
			// 整数に変換して 2 桁表示で結合
			form.setTrainingStartTime(String.format("%02d:%02d",
					Integer.parseInt(startHour),
					Integer.parseInt(startMinute)));
		} else {
			form.setTrainingStartTime(null); // 入力がなければ null
		}

		// 退勤時間の結合
		String endHour = form.getTrainingEndTimeHour();
		String endMinute = form.getTrainingEndTimeMinute();
		if (endHour != null && !endHour.isEmpty() && endMinute != null && !endMinute.isEmpty()) {
			form.setTrainingEndTime(String.format("%02d:%02d",
					Integer.parseInt(endHour),
					Integer.parseInt(endMinute)));
		} else {
			form.setTrainingEndTime(null);
		}

		return form;
	}
	
//	public List<String> validateAttebdance(AttendanceForm form) throws ParseException{
//		List<String> errorMessages = new ArrayList<>();
//		
//		int index = 1; //n番目特定
//		
//		for(DailyAttendanceForm daily : form.getAttendanceList()) {
//			
//			//備考の文字数チェック
//			if(daily.getNote() != null && daily.getNote().length() > 100) {
//				String msg = messageUtil.getMessage("maxlength", new String[] {"備考","100"});
//				errorMessages.add(msg);
//			}
//			//出勤時間の一部未入力
//			boolean hasStartHour = daily.getTrainingStartTimeHour() != null && !daily.getTrainingStartTimeHour().isEmpty();
//			boolean hasStartMinute = daily.getTrainingEndTimeMinute() !=null && !daily.getTrainingStartTimeMinute().isEmpty();
//			if((hasStartHour && !hasStartMinute) || (!hasStartHour && hasStartMinute)) {
//			
//				String msg = messageUtil.getMessage("input.invalid" , new String[] {"出勤時間"});
//				errorMessages.add(msg);
//				
//			}
//			
//			//退勤時間の一部未入力
//			boolean hasEndHour = daily.getTrainingEndTimeHour() != null && !daily.getTrainingEndTimeHour().isEmpty();
//			boolean hasEndMinute = daily.getTrainingEndTimeMinute() != null && !daily.getTrainingEndTimeMinute().isEmpty();
//			if((hasEndHour && !hasEndMinute) || (!hasEndHour && hasEndMinute)) {
//				String msg = messageUtil.getMessage("input.invalid" , new String[] {"退勤時間"});
//				errorMessages.add(msg);
//			}
//			
//			//出勤なし 退勤あり
//			if(!hasStartHour && !hasStartMinute && (hasEndHour || hasEndMinute)) {
//				String msg = messageUtil.getMessage("attendance.punchInEmpty");
//				errorMessages.add(msg);
//			}
//			
//			//出勤が退勤より多い
//			if(hasStartHour && hasStartMinute && hasEndHour && hasEndMinute)  {
//				try {
//					SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
//					Date strat = sdf.parse(daily.getTrainingStartTimeHour() + ":" + daily.getTrainingStartTimeMinute());
//					Date end = sdf.parse(daily.getTrainingEndTimeHour() + ":" + daily.getTrainingEndTimeMinute());
//				if(strat.after(end)) {
//					String msg = messageUtil.getMessage("attendance.trainingTimeRange",new String[] {String.valueOf(index)});
//					errorMessages.add(msg);
//				}
//				}catch(ParseException e) {
//					e.printStackTrace();
//				}
//			}
//				
//			//中抜け時間が勤務時間を超える
//			if(hasStartHour && hasStartMinute && hasEndHour && hasEndMinute && daily.getBlankTime() != null) {
//				try {
//					SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
//					Date strat = sdf.parse(daily.getTrainingStartTimeHour() + ":" + daily.getTrainingStartTimeMinute());
//					Date end = sdf.parse(daily.getTrainingEndTimeHour() + ":" + daily.getTrainingEndTimeMinute());
//					long workMinutes =(end.getTime() - strat.getTime()) / (1000 * 60);
//					int blankMinutes = daily.getBlankTime();
//					
//					if(blankMinutes > workMinutes) {
//						String msg = messageUtil.getMessage("attendance.blankTimeError");
//						errorMessages.add(msg);
//					}
//				}catch(ParseException e) {
//					e.printStackTrace();
//				}
//				}
//			index++;
//			}


	
	public String validateAttendance(DailyAttendanceForm dailyForm) {
		String startHour = dailyForm.getTrainingStartTimeHour();
		String startMinute = dailyForm.getTrainingStartTimeMinute();
		String endHour = dailyForm.getTrainingEndTimeHour();
		String endMinute = dailyForm.getTrainingEndTimeMinute();
	
	
	//出勤時間入力チェック
	if((startHour != null && !startHour.isEmpty()) && (startMinute == null || startMinute.isEmpty())) {
		return messageUtil.getMessage(Constants.VALID_KEY_INVALID,new String[]{"出勤時間"});
	}
	if((startMinute != null && !startMinute.isEmpty()) && (startHour == null || startHour.isEmpty())) {
		return messageUtil.getMessage(Constants.VALID_KEY_INVALID,new String[]{"退勤時間"});
		
		}
	
	//退勤時間入力チェック
	if((endHour != null && !endHour.isEmpty()) && (endMinute == null || endMinute.isEmpty())) {
		return messageUtil.getMessage("end.invalid");
	}
	if((endMinute != null && !endMinute.isEmpty()) && (endHour == null || endHour.isEmpty())) {
		return messageUtil.getMessage("end.invalid");
	}
	//出退勤の前後関係チェック
	if(startHour != null && !startHour.isEmpty() && 
		startMinute !=null && !startMinute.isEmpty() &&
		endHour !=null && !endHour.isEmpty() &&
		endMinute != null && !endMinute.isEmpty()) {
		
		int startTotal = Integer.parseInt(startHour) * 60 + Integer.parseInt(startMinute);
		int endTotal = Integer.parseInt(endHour) * 60 + Integer.parseInt(endMinute);
		
		if(endTotal < startTotal) {
			return messageUtil.getMessage("input.invalid");
		}
	}
	return null; //エラーなし
	
	}
	
	
}
