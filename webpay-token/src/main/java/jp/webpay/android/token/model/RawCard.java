package jp.webpay.android.token.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

import jp.webpay.android.token.validator.CardNumberValidator;
import jp.webpay.android.token.validator.CvcValidator;
import jp.webpay.android.token.validator.ExpiryValidator;
import jp.webpay.android.token.validator.NameValidator;

public class RawCard {

    private String number;
    private Integer expYear;
    private Integer expMonth;
    private String name;
    private String cvc;

    public RawCard number(String number) {
        this.number = number;
        return this;
    }

    public RawCard expYear(Integer expYear) {
        this.expYear = expYear;
        return this;
    }

    public RawCard expMonth(Integer expMonth) {
        this.expMonth = expMonth;
        return this;
    }

    public RawCard name(String name) {
        this.name = name;
        return this;
    }

    public RawCard cvc(String cvc) {
        this.cvc = cvc;
        return this;
    }

    public JSONObject toJson() {
        JSONObject card = new JSONObject();
        try {
            card.put("number", number);
            card.put("exp_month", expMonth);
            card.put("exp_year", expYear);
            card.put("cvc", cvc);
            card.put("name", name);
            JSONObject root = new JSONObject();
            root.put("card", card);
            return root;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean cvcIsValid() {
        return CvcValidator.isValid(cvc);
    }

    public boolean expiryIsValid() {
        return ExpiryValidator.isValid(expMonth, expYear);
    }

    public boolean nameIsValid() {
        return NameValidator.isValid(name);
    }

    public boolean numberIsValid() {
        return CardNumberValidator.isValid(number);
    }

    public boolean numberIsValid(List<CardType> cardTypes) {
        return CardNumberValidator.isValid(number, cardTypes);
    }
}
