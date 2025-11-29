package com.mjc.mascotalink.modelo;

import java.util.Date;

/**
 * Representa un separador de fecha en el chat.
 */
public class DateSeparator implements ChatItem {
    private String dateText;
    private Date date;
    
    public DateSeparator(String dateText, Date date) {
        this.dateText = dateText;
        this.date = date;
    }
    
    @Override
    public int getType() {
        return TYPE_DATE_SEPARATOR;
    }
    
    public String getDateText() {
        return dateText;
    }
    
    public void setDateText(String dateText) {
        this.dateText = dateText;
    }
    
    public Date getDate() {
        return date;
    }
    
    public void setDate(Date date) {
        this.date = date;
    }
}
