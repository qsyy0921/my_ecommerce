package cn.bugstack.infrastructure.adapter.support;

import cn.bugstack.infrastructure.dao.po.ThirdPartyBill;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ThirdPartyBillCsvParser {

    public List<ThirdPartyBill> parse(String csvText) {
        List<ThirdPartyBill> billList = new ArrayList<>();
        if (null == csvText || csvText.trim().isEmpty()) {
            return billList;
        }
        String[] lines = csvText.split("\\r?\\n");
        for (String line : lines) {
            ThirdPartyBill bill = parseLine(line);
            if (null != bill) {
                billList.add(bill);
            }
        }
        return billList;
    }

    private ThirdPartyBill parseLine(String line) {
        if (null == line || line.trim().isEmpty() || line.startsWith("billNo,")) {
            return null;
        }
        String[] columns = line.split(",", -1);
        if (columns.length < 8) {
            return null;
        }
        return ThirdPartyBill.builder()
                .billNo(columns[0].trim())
                .orderId(columns[1].trim())
                .channel(columns[2].trim())
                .channelTradeNo(columns[3].trim())
                .billType(columns[4].trim())
                .amount(new BigDecimal(columns[5].trim()))
                .billStatus(columns[6].trim())
                .billTime(parseBillTime(columns[7].trim()))
                .rawLine(line)
                .build();
    }

    private Date parseBillTime(String billTime) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(billTime);
        } catch (ParseException e) {
            return new Date();
        }
    }

}
