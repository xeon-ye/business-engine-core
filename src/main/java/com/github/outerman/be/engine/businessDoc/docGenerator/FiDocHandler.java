package com.github.outerman.be.engine.businessDoc.docGenerator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.outerman.be.api.constant.BusinessTypeUtil;
import com.github.outerman.be.api.vo.AcmSortReceipt;
import com.github.outerman.be.api.vo.AcmSortReceiptDetail;
import com.github.outerman.be.api.vo.AcmSortReceiptSettlestyle;
import com.github.outerman.be.api.vo.DocAccountTemplateItem;
import com.github.outerman.be.api.vo.FiDocDto;
import com.github.outerman.be.api.vo.FiDocEntryDto;
import com.github.outerman.be.api.vo.PaymentTemplateItem;
import com.github.outerman.be.api.vo.SetCurrency;
import com.github.outerman.be.api.vo.SetOrg;
import com.github.outerman.be.engine.businessDoc.BusinessUtil;
import com.github.outerman.be.engine.businessDoc.businessTemplate.AmountGetter;
import com.github.outerman.be.engine.util.DoubleUtil;
import com.github.outerman.be.engine.util.StringUtil;

public class FiDocHandler {

    private SetOrg org;

    private SetCurrency currency;

    private AcmSortReceipt receipt;

    private FiDocDto fiDocDto;

    /** 借方主科目分录 */
    private List<FiDocEntryDto> entryList1 = new ArrayList<>();
    /** 借方税科目分录 */
    private List<FiDocEntryDto> entryList2 = new ArrayList<>();
    /** 贷方主科目分录 */
    private List<FiDocEntryDto> entryList3 = new ArrayList<>();
    /** 贷方税科目分录 */
    private List<FiDocEntryDto> entryList4 = new ArrayList<>();
    /** 凭证模板中本表自平分录 */
    private List<FiDocEntryDto> entryList5 = new ArrayList<>();

    private Map<String, FiDocEntryDto> entryMap = new HashMap<>();;

    public FiDocHandler(SetOrg org, SetCurrency currency, AcmSortReceipt receipt) {
        this.org = org;
        this.currency = currency;
        this.receipt = receipt;
        this.fiDocDto = getDefaultDoc(receipt);
    }

    public FiDocDto getFiDocDto() {
        List<FiDocEntryDto> entrys = new ArrayList<>();
        entrys.addAll(entryList5);
        entrys.addAll(entryList1);
        entrys.addAll(entryList2);
        entrys.addAll(entryList3);
        entrys.addAll(entryList4);
        fiDocDto.setEntrys(entrys);
        return fiDocDto;
    }

    /**
     * 根据流水账信息获取默认的凭证 dto
     * 
     * @param receipt
     */
    private FiDocDto getDefaultDoc(AcmSortReceipt receipt) {
        FiDocDto doc = new FiDocDto();
        doc.setSourceVoucherId(receipt.getSourceVoucherId());
        doc.setSourceVoucherCode(receipt.getSourceVoucherCode());
        doc.setDocSourceTypeId(receipt.getSourceVoucherTypeId());
        doc.setAttachedVoucherNum(receipt.getAppendNum());
        String dateStr = StringUtil.format(receipt.getInAccountDate());
        doc.setVoucherDate(dateStr);
        // 如果曾经生成过,保留凭证号
        if (!StringUtil.isEmpty(receipt.getDocCodeBak())) {
            doc.setCode(receipt.getDocCodeBak());
        }
        return doc;
    }

    public void addEntry(DocAccountTemplateItem docTemplate, AcmSortReceiptDetail detail) {
        InnerFiDocEntryDto innerEntry = getDocEntryDto(docTemplate, detail);
        String key = innerEntry.getKey();
        FiDocEntryDto entry = innerEntry.getFiDocEntryDto();
        if (entryMap.containsKey(key)) {
            // 按照分录合并规则需要合并
            Double quantity = entry.getQuantity();
            FiDocEntryDto existEntry = entryMap.get(key);
            if (quantity != null || existEntry.getQuantity() != null) {
                existEntry.setQuantity(DoubleUtil.add(quantity, existEntry.getQuantity()));
            }
            existEntry.setAmountCr(DoubleUtil.add(existEntry.getAmountCr(), entry.getAmountCr()));
            existEntry.setOrigAmountCr(DoubleUtil.add(existEntry.getOrigAmountCr(), entry.getOrigAmountCr()));
            existEntry.setAmountDr(DoubleUtil.add(existEntry.getAmountDr(), entry.getAmountDr()));
            existEntry.setOrigAmountDr(DoubleUtil.add(existEntry.getOrigAmountDr(), entry.getOrigAmountDr()));
            Double amount = entry.getAmountCr();
            if (DoubleUtil.isNullOrZero(amount)) {
                amount = entry.getAmountDr();
            }
            existEntry.setPrice(DoubleUtil.div(amount, existEntry.getQuantity()));
        } else {
            // 新增分录，按照排序规则放到指定位置
            entryMap.put(key, entry);
            // 本表自评时
            if (!docTemplate.getIsSettlement() || BusinessTypeUtil.SPECIAL_ORDER.contains(docTemplate.getBusinessCode())) {
                addSpecialEntry(entry);
            } else {
                addEntry(entry);
            }
        }
    }

    private InnerFiDocEntryDto getDocEntryDto(DocAccountTemplateItem docTemplate, AcmSortReceiptDetail detail) {
        Double amount = AmountGetter.getAmount(detail, docTemplate);
        if (DoubleUtil.isNullOrZero(amount)) {
            return null;
        }

        StringBuilder key = new StringBuilder();
        FiDocEntryDto entry = new FiDocEntryDto();

        String summary;
        if (!StringUtil.isEmpty(docTemplate.getSummary())) {
            summary = docTemplate.getSummary();
        } else {
            summary = detail.getMemo();
        }
        key.append("_summary").append(summary);
        entry.setSummary(summary);

        entry.setAccountId(docTemplate.getAccountId());
        entry.setAccountCode(docTemplate.getAccountCode());
        key.append(docTemplate.getAccountCode());
        entry.setSourceFlag(docTemplate.getFlag());

        key.append("_businessType").append(detail.getBusinessType());
        entry.setSourceBusinessTypeId(detail.getBusinessType());

        if (docTemplate.getIsAuxAccCalc() != null && docTemplate.getIsAuxAccCalc()) {
            if (docTemplate.getIsAuxAccDepartment() != null && docTemplate.getIsAuxAccDepartment()) {// 部门
                entry.setDepartmentId(detail.getDepartment());
                key.append("_departmentId").append(detail.getDepartment());
            }
            if (docTemplate.getIsAuxAccPerson() != null && docTemplate.getIsAuxAccPerson()) {// 人员
                entry.setPersonId(detail.getEmployee());
                key.append("_personId").append(detail.getEmployee());
            }
            if (docTemplate.getIsAuxAccCustomer()) {// 客户
                entry.setCustomerId(detail.getConsumer());
                key.append("_customerId").append(detail.getConsumer());
            }
            if (docTemplate.getIsAuxAccSupplier()) {// 供应商
                entry.setSupplierId(detail.getVendor());
                key.append("_supplierId").append(detail.getVendor());
            }
            if (docTemplate.getIsAuxAccInventory()) {// 存货
                if (detail.getAssetId() != null) {
                    entry.setInventoryId(detail.getAssetId());
                    key.append("_inventoryId").append(detail.getAssetId());
                } else {
                    entry.setInventoryId(detail.getInventory());
                    key.append("_inventoryId").append(detail.getInventory());
                }
            }
            if (docTemplate.getIsAuxAccProject()) {// 项目
                entry.setProjectId(detail.getProject());
                key.append("_projectId").append(detail.getProject());
            }
            if(docTemplate.getIsQuantityCalc()){ // 数量辅助核算时，值传给凭证，不作为分组的依据
                entry.setQuantity(detail.getCommodifyNum());
            }
            // 银行账号
            if ("402000".equals(docTemplate.getBusinessCode().toString()) && "A".equals(docTemplate.getFlag())) {
                key.append("_bankAccountId").append(detail.getInBankAccountId());
                entry.setBankAccountId(detail.getInBankAccountId());
            } else {
                key.append("_bankAccountId").append(detail.getBankAccountId());
                entry.setBankAccountId(detail.getBankAccountId());
            }
            if (docTemplate.getIsMultiCalc()) { // 多币种
                entry.setCurrencyId(currency.getId());
                key.append("_currencyId").append(currency.getId());
            }
            // 即征即退，影响合并
            if (docTemplate.getIsAuxAccLevyAndRetreat() != null && docTemplate.getIsAuxAccLevyAndRetreat()) {
                if (detail.getDrawbackPolicy() != null) {
                    entry.setLevyAndRetreatId(detail.getDrawbackPolicy());
                    key.append("_levyAndRetreatId").append(detail.getDrawbackPolicy());
                }
            }
        }
        // TODO notesNum invoiceType taxRate
        // 单价 看是否抵扣然后传递不同的单价
        Boolean isDeduction = detail.getIsDeduction() != null && detail.getIsDeduction() == 1;
        if (BusinessUtil.paymentDirection(detail.getBusinessCode()) == 1 || isDeduction) {
            entry.setPrice(detail.getPrice());
        } else {
            entry.setPrice(detail.getTaxInclusivePrice());
        }

        if (!BusinessTypeUtil.GONGZI_VOUCHERTYPE_LIST.contains(receipt.getSourceVoucherId())) {
            // 科目+辅助核算+业务类型+部门属性+人员属性+借款期限+账户属性流入+账户属性流出+纳税人+资产类别+罚款性质
            String influence = docTemplate.getInfluence();
            if (!StringUtil.isEmpty(influence)) {
                if (influence.equals("departmentAttr")) { // 部门属性
                    key.append("_departmentAttr").append(detail.getDepartmentProperty());
                } else if (influence.equals("departmentAttr,personAttr")) { // 部门属性、人员属性
                    key.append("_departmentAttr").append(detail.getDepartmentProperty());
                    key.append("_personAttr").append(detail.getEmployeeAttribute());
                } else if (influence.equals("vatTaxpayer")) { // 纳税人性质；vatTaxpayer,qualification、vatTaxpayer,taxType
                    key.append("_vatTaxpayer").append(org.getVatTaxpayer());
                } else if (influence.equals("punishmentAttr")) { // 罚款性质
                    key.append("_punishmentAttr").append(detail.getPenaltyType());
                } else if (influence.equals("borrowAttr")) { // 借款期限
                    key.append("_borrowAttr").append(detail.getLoanTerm());
                } else if (influence.equals("assetAttr")) { // 资产类别
                    key.append("_assetAttr").append(detail.getAssetAttr());
                } else if (influence.equals("accountInAttr")) { // 流入账户属性
                    key.append("_accountInAttr").append(detail.getInBankAccountId());
                } else if (influence.equals("accountOutAttr")) { // 流出账户属性
                    key.append("_accountOutAttr").append(detail.getBankAccountId());
                }
                // TODO formula inventoryAttr
            }
        }
        // 0 借 1 贷，流水账不区分币种，本币原币金额一样
        Boolean direction = docTemplate.getDirection();
        if (direction) {
            entry.setAmountCr(amount);
            entry.setOrigAmountCr(amount);
        } else {
            entry.setAmountDr(amount);
            entry.setOrigAmountDr(amount);
        }

        InnerFiDocEntryDto result = new InnerFiDocEntryDto();
        result.setFiDocEntryDto(entry);
        result.setKey(key.toString());
        return result;
    }

    private void addSpecialEntry(FiDocEntryDto entry) {
        if (entryList5.size() == 0) {
            entryList5.add(entry);
            return;
        }

        // 同时满足以下条件, 允许上移:
        // 1)新插入金额在借方, 上一条金额在贷方
        // 2)上下两条是同一个业务或分录摘要一致
        boolean isDebit = DoubleUtil.isNullOrZero(entry.getAmountDr());
        if (!isDebit) {
            entryList5.add(entry);
            return;
        }

        Long businessTypeId = entry.getSourceBusinessTypeId();
        int index;
        for (index = entryList5.size(); index > 0; index--) {
            FiDocEntryDto lastEntry = entryList5.get(index - 1);
            boolean lastIsDebit = DoubleUtil.isNullOrZero(lastEntry.getAmountDr());
            Long lastBusinessTypeId = lastEntry.getSourceBusinessTypeId();
            if (lastIsDebit) {
                break;
            }
            if (!businessTypeId.equals(lastBusinessTypeId) && !lastEntry.getSummary().equals(entry.getSummary())) {
                break;
            }
        }
        entryList5.add(index, entry);
    }

    private void addEntry(FiDocEntryDto entry) {
        boolean isDebit = DoubleUtil.isNullOrZero(entry.getAmountDr());
        String accountCode = entry.getAccountCode();
        List<FiDocEntryDto> entryList;
        if (isDebit) {
            if (!accountCode.startsWith("2221")) {
                entryList = entryList1;
            } else {
                entryList = entryList2;
            }
        } else {
            if (!accountCode.startsWith("2221")) {
                entryList = entryList3;
            } else {
                entryList = entryList4;
            }
        }

        if (entryList.isEmpty()) {
            entryList.add(entry);
            return;
        }

        int index = entryList.size();
        for (FiDocEntryDto item : entryList) {
            String code = item.getAccountCode();
            if (accountCode.compareTo(code) < 0) {
                index = entryList.indexOf(item);
                break;
            }
        }
        entryList.add(index, entry);
    }

    public void addEntry(PaymentTemplateItem payDocTemplate, AcmSortReceiptSettlestyle settle) {
        FiDocEntryDto entry = getDocEntryDto(payDocTemplate, settle);
        if (entry == null) {
            return;
        }
        addEntry(entry);
    }

    private FiDocEntryDto getDocEntryDto(PaymentTemplateItem payDocTemplate, AcmSortReceiptSettlestyle settle) {
        Double amount = 0.0;
        if (AmountGetter.AMOUNT_TAXINCLUSIVEAMOUNT.equals(payDocTemplate.getFundSource())) {
            amount = settle.getTaxInclusiveAmount();
        }
        if (DoubleUtil.isNullOrZero(amount)) {
            return null;
        }

        FiDocEntryDto entry = new FiDocEntryDto();
        if (payDocTemplate.getIsAuxAccCalc() != null && payDocTemplate.getIsAuxAccCalc()) {
            if (payDocTemplate.getIsAuxAccPerson()) { // 人员
                entry.setPersonId(settle.getEmployee());
            }
            if (payDocTemplate.getIsAuxAccCustomer()) {// 客户
                entry.setCustomerId(settle.getConsumer());
            }
            if (payDocTemplate.getIsAuxAccSupplier()) {// 供应商
                entry.setSupplierId(settle.getVendor());
            }
            if (payDocTemplate.getIsAuxAccBankAccount()) {// 银行账号
                entry.setBankAccountId(settle.getBankAccountId());
            }
            if (payDocTemplate.getIsMultiCalc()) {// 多币种
                entry.setCurrencyId(currency.getId());
            }
        }

        String summary = getSettleSummary(settle, payDocTemplate);
        entry.setSummary(summary);
        entry.setAccountId(payDocTemplate.getAccountId());
        entry.setAccountCode(payDocTemplate.getSubjectDefault());

        // 0 借 1 贷，流水账不区分币种，本币原币金额一样
        Boolean direction = payDocTemplate.getDirection();
        // 处理分录借贷方向、正负金额是否需要颠倒
        if (payDocTemplate.getReversal() && amount < 0) {
            direction = !direction;
            amount*= -1;
        }
        if (direction) {
            entry.setAmountCr(amount);
            entry.setOrigAmountCr(amount);
        } else {
            entry.setAmountDr(amount);
            entry.setOrigAmountDr(amount);
        }

        return entry;
    }

    /**
     * 获取结算情况明细对应的分录摘要
     * @param settle
     * @param receipt
     * @param payDocTemplate
     * @return
     */
    private String getSettleSummary(AcmSortReceiptSettlestyle settle, PaymentTemplateItem payDocTemplate) {
        String summary = settle.getMemo();
        if (!StringUtil.isEmpty(summary)) {
            return summary;
        }
        if (receipt.getAcmSortReceiptSettlestyleList().size() != 1) {
            summary = payDocTemplate.getSubjectType();
        } else {
            summary = receipt.getAcmSortReceiptDetailList().get(0).getMemo();
            if (StringUtil.isEmpty(summary)) {
                summary = payDocTemplate.getSubjectType();
            }
        }
        return summary;
    }

    class InnerFiDocEntryDto {

        private FiDocEntryDto fiDocEntryDto;

        private String key;

        public FiDocEntryDto getFiDocEntryDto() {
            return fiDocEntryDto;
        }

        public void setFiDocEntryDto(FiDocEntryDto fiDocEntryDto) {
            this.fiDocEntryDto = fiDocEntryDto;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

    }
}
