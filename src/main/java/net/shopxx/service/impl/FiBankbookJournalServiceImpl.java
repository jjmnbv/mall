package net.shopxx.service.impl;

import java.math.BigDecimal;
import java.util.Date;

import javax.inject.Inject;

import net.shopxx.Page;
import net.shopxx.Pageable;
import net.shopxx.dao.FiBankbookJournalDao;
import net.shopxx.entity.Country;
import net.shopxx.entity.FiBankbookBalance;
import net.shopxx.entity.FiBankbookJournal;
import net.shopxx.entity.FiBankbookJournal.Type;
import net.shopxx.entity.Member;
import net.shopxx.service.FiBankbookBalanceService;
import net.shopxx.service.FiBankbookJournalService;
import net.shopxx.service.MemberService;
import net.shopxx.util.SpringUtils;

import org.apache.commons.beanutils.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service - 交易记录
 * 
 * @author gaoxiang
 * @version 5.0.3
 */
@Service
public class FiBankbookJournalServiceImpl extends BaseServiceImpl<FiBankbookJournal, Long> implements FiBankbookJournalService {
	
	@Inject
	private FiBankbookJournalDao fiBankbookJournalDao;
	
	@Inject
	private FiBankbookBalanceService fiBankbookBalanceService;
	
	@Inject
	private MemberService memberService;
	
	/**
	 * 查找交易记录分页
	 * 
	 * @param country
	 *            国家
	 * @param type
	 *            账户类型
	 * @param moneyType
	 *            资金类型
	 * @param beginDate
	 *            起始日期
	 * @param endDate
	 *            结束日期
	 * @param pageable
	 *            分页信息
	 * @return 交易记录分页
	 */
	public Page<FiBankbookJournal> findPage(Country country, FiBankbookJournal.Type type, FiBankbookJournal.MoneyType moneyType, Date beginDate, Date endDate, Pageable pageable){
		return fiBankbookJournalDao.findPage(country, type, moneyType, beginDate, endDate, pageable);
	}
	/**
	 * 查找个人交易记录分页
	 * 
	 * @param currentUser
	 *            会员信息
	 * @param pageable
	 *            分页信息
	 * @return 交易记录分页
	 */
	@Transactional(readOnly = true)
	public Page<FiBankbookJournal> findPageByMemberId(Type type,Member currentUser, Pageable pageNumber){
		return fiBankbookJournalDao.findPageByMemberId(type,currentUser, pageNumber);
	}
	/**
	 * 
	 * @param currentUser
	 * @param coupon
	 * @param string
	 * 			“out”转出
	 * @param string2
	 * 			“in”获取
	 * @return
	 */
	public int countN(Member currentUser, FiBankbookJournal.Type coupon, String string, String string2){
		return fiBankbookJournalDao.countN(currentUser, coupon,string,string2);
	}
	/**
	 * 查找个人充值记录
	 * 
	 * @param member
	 *            会员信息
	 * @param code
	 *            编号
	 * @return 交易记录分页
	 */
	@Transactional(readOnly = true)
	public FiBankbookJournal findByMemberAndCode(Member member, String code){
		return fiBankbookJournalDao.findByMemberAndCode(member,code);
	}
	/**
	 * 获取最近的一条记录
	 * @param member
	 * @param type
	 * @return
	 */
	public FiBankbookJournal findLastByMember(Member member, FiBankbookJournal.Type type){
		return fiBankbookJournalDao.findLastByMember(member, type);
	}
	
	/**
	 * 根据会员编号充值购物券接口
	 * 
	 * @param usercode 会员编号
	 * @param money 资金
	 * @param uniqueCode 交易单号
	 * @param type 0:电子币账户  1:购物券账户
	 * @param dealType 0:存入  1取出
	 * @param moneyType 0:现金  1:Pos刷卡  2:线下转账 3:余额退款  4:财务冲账 5:保证金 6:手续费 7:退单退款  8:订单审核 9:物流费 10:在线充值 11:购物券转入 12:购物券转出
	 * @param notes 摘要
	 * @return
	 * @throws Exception
	 */
	@Transactional(rollbackFor = Exception.class)
	public String recharge(String usercode, BigDecimal money, String uniqueCode, FiBankbookJournal.Type type, FiBankbookJournal.DealType dealType, FiBankbookJournal.MoneyType moneyType, String notes) throws Exception{
//		FiBankbookJournal.Type eType = null;
//		FiBankbookJournal.DealType eDealType = null;
//		FiBankbookJournal.MoneyType eMoneyType = null;
//		try {
//			eType = FiBankbookJournal.Type.values()[type];
//			eDealType = FiBankbookJournal.DealType.values()[dealType];
//			eMoneyType = FiBankbookJournal.MoneyType.values()[moneyType];
//		} catch (Exception e) {
//			throw new Exception("type or dealType or moneyType value is illegal");
//		}
		
		// 获取会员
		Member memberTemp = memberService.findByUsercode(usercode);
		if (null == memberTemp) {
			throw new Exception("member is not exist");
		}
		Member member = new Member();
		BeanUtils.copyProperties(member, memberTemp);
		// 金额不可以为0
		if (money.compareTo(BigDecimal.ZERO) == 0) {
			throw new Exception("amount is 0");
		}
		BigDecimal amount = money;
		// 获取需要更新的金额  存入是add 取出是subtract
		if (FiBankbookJournal.DealType.takeout == dealType) {
			BigDecimal zero = new BigDecimal(0);
			amount = zero.subtract(amount);
		}
		Country country = member.getCountry();
		// 获取余额类型
		FiBankbookBalance.Type balanceType = FiBankbookBalance.Type.balance;
		if (FiBankbookJournal.Type.coupon == type) {
			balanceType = FiBankbookBalance.Type.coupon;
		}
		// 获取用户余额
		FiBankbookBalance fiBankbookBalance = fiBankbookBalanceService.find(member, balanceType);
		// 获取最近的fiBankbookJournal记录
		FiBankbookJournal lastFiBankbookJournal = findLastByMember(member, type);
		// 最近一笔交易费用的钱
		BigDecimal lastMoney = null;
		if (null != lastFiBankbookJournal) {
			lastMoney = lastFiBankbookJournal.getMoney();
		}
		// 用户余额为空则新增一条记录
		if (null == fiBankbookBalance) {
			fiBankbookBalance = new FiBankbookBalance();
			fiBankbookBalance.setBalance(amount);
			fiBankbookBalance.setCountry(country);
			fiBankbookBalance.setMember(member);
			fiBankbookBalance.setType(balanceType);
			fiBankbookBalanceService.save(fiBankbookBalance);
		}
		// 更新用户余额
		else {
			fiBankbookBalance.setMember(member);
			// 最近一笔交易记录不存在，则需要交易余额是否为0，为0则可以更新用户余额
			if (null == lastFiBankbookJournal) {
				if (fiBankbookBalance.getBalance().doubleValue() != 0){
					throw new Exception(SpringUtils.getMessage("admin.fiBankbookJournalTemp.error.balance.zero", member.getUsercode()));
				}
				// 更新用户的余额
				fiBankbookBalanceService.addBalance(fiBankbookBalance, member, amount);
			}
			// 如果最近的一条记录不为空并且用户余额不为空，校验交易记录的最近一条的余额和用户的余额是否一致
			else {
				if (fiBankbookBalance.getBalance().doubleValue() != lastFiBankbookJournal.getBalance().doubleValue()){
					throw new Exception(SpringUtils.getMessage("admin.fiBankbookJournalTemp.error.balance.error", member.getUsercode(), fiBankbookBalance.getBalance(), lastFiBankbookJournal.getBalance()));
				}
				// 更新用户的余额
				fiBankbookBalanceService.addBalance(fiBankbookBalance, member, amount);
			}
		}
		
		// 新增一条记录到fiBankbookJournal表
		FiBankbookJournal fiBankbookJournal = new FiBankbookJournal();
		fiBankbookJournal.setBalance(fiBankbookBalance.getBalance());
		fiBankbookJournal.setCountry(country);
		fiBankbookJournal.setCreaterCode(member.getUsercode());
		fiBankbookJournal.setCreaterName(member.getUsername());
		fiBankbookJournal.setDealDate(new Date());
		fiBankbookJournal.setDealType(dealType);
		fiBankbookJournal.setFiBankbookJournalTemp(null);
		fiBankbookJournal.setLastFiBankbookJournal(lastFiBankbookJournal);
		fiBankbookJournal.setLastMoney(lastMoney);
		fiBankbookJournal.setMember(member);
		fiBankbookJournal.setMoney(money);
		fiBankbookJournal.setMoneyType(moneyType);
		fiBankbookJournal.setNotes(notes);
		fiBankbookJournal.setRemark(notes);
		fiBankbookJournal.setType(type);
		fiBankbookJournal.setUniqueCode(uniqueCode);
		save(fiBankbookJournal);
		return "success";
	}
	
	@Override
	@Transactional
	public FiBankbookJournal save(FiBankbookJournal fiBankbookJournal) {
		return super.save(fiBankbookJournal);
	}

	@Override
	@Transactional
	public FiBankbookJournal update(FiBankbookJournal fiBankbookJournal) {
		return super.update(fiBankbookJournal);
	}

	@Override
	@Transactional
	public FiBankbookJournal update(FiBankbookJournal fiBankbookJournal, String... ignoreProperties) {
		return super.update(fiBankbookJournal, ignoreProperties);
	}

	@Override
	@Transactional
	public void delete(Long id) {
		super.delete(id);
	}

	@Override
	@Transactional
	public void delete(Long... ids) {
		super.delete(ids);
	}

	@Override
	@Transactional
	public void delete(FiBankbookJournal fiBankbookJournal) {
		super.delete(fiBankbookJournal);
	}

}
