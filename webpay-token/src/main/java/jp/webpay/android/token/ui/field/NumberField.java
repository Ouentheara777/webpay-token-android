package jp.webpay.android.token.ui.field;

import android.content.Context;
import android.text.InputType;
import android.util.AttributeSet;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import jp.webpay.android.token.R;
import jp.webpay.android.token.model.CardType;
import jp.webpay.android.token.model.RawCard;
import jp.webpay.android.token.validator.CardNumberValidator;

public class NumberField extends MultiColumnCardField {
    public static final String SEPARATOR = " ";
    private String mValidNumber;
    private OnCardTypeChangeListener mOnCardTypeChangeListener;
    private CardType mCurrentCardType;
    private List<CardType> mCardTypesSupported;

    public NumberField(Context context) {
        super(context, SEPARATOR);
        initialize();
    }

    public NumberField(Context context, AttributeSet attrs) {
        super(context, attrs, SEPARATOR);
        initialize();
    }

    public NumberField(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle, SEPARATOR);
        initialize();
    }

    private void initialize() {
        setInputType(InputType.TYPE_CLASS_NUMBER);
        setHint(R.string.field_number_hint);
    }

    @Override
    protected boolean validateCurrentValue() {
        String value = getText().toString().replace(SEPARATOR, "");
        if (CardNumberValidator.isValid(value, mCardTypesSupported)) {
            mValidNumber = value;
            return true;
        } else {
            mValidNumber = null;
            return false;
        }
    }

    @Override
    public void updateCard(RawCard card) {
        card.number(mValidNumber);
    }

    @Override
    protected String formatVisibleText(String current) {
        CardType cardType = expectCardType(current);
        List<Integer> separatorIndex;
        if (cardType == CardType.AMERICAN_EXPRESS
                || cardType == CardType.DINERS_CLUB) {
            separatorIndex = Arrays.asList(4, 10);
        } else {
            separatorIndex = Arrays.asList(4, 8, 12);
        }

        StringBuilder builder = new StringBuilder();
        int validChars = 0;
        for (int i = 0; i < current.length(); i++) {
            char ch = current.charAt(i);
            if (ch >= '0' && ch <= '9') {
                builder.append(ch);
                validChars += 1;
                if (validChars >= 16)
                    break;

                if (separatorIndex.contains(validChars)) {
                    builder.append(SEPARATOR);
                }
            }
        }
        String visibleText = builder.toString();
        notifyCardTypeChange(visibleText);
        return visibleText;
    }

    private CardType expectCardType(String number) {
        if (Pattern.matches("4[0-9].*", number)) {
            return CardType.VISA;
        }
        if (Pattern.matches("3[47].*", number)) {
            return CardType.AMERICAN_EXPRESS;
        }
        if (Pattern.matches("5[1-5].*", number)) {
            return CardType.MASTERCARD;
        }
        if (Pattern.matches("3[0689].*", number)) {
            return CardType.DINERS_CLUB;
        }
        if (Pattern.matches("35.*", number)) {
            return CardType.JCB;
        }
        return null;
    }

    private void notifyCardTypeChange(String number) {
        CardType cardType = expectCardType(number);
        boolean isSame = mCurrentCardType == null ? cardType == null : mCurrentCardType.equals(cardType);
        if (!isSame) {
            mCurrentCardType = cardType;
            if (mOnCardTypeChangeListener != null) {
                mOnCardTypeChangeListener.onCardTypeChange(cardType);
            }
        }
    }

    public String getValidNumber() {
        return mValidNumber;
    }

    public void setOnCardTypeChangeListener(OnCardTypeChangeListener mListener) {
        this.mOnCardTypeChangeListener = mListener;
    }

    public void setCardTypesSupported(List<CardType> cardTypesSupported) {
        this.mCardTypesSupported = cardTypesSupported;
    }

    public static interface OnCardTypeChangeListener {
        public void onCardTypeChange(CardType cardType);
    }
}
