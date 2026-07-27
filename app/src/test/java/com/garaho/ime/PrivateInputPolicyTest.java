package com.garaho.ime;

import android.text.InputType;
import android.view.inputmethod.EditorInfo;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PrivateInputPolicyTest {

    @Test
    public void recognizesEveryPasswordVariation() {
        assertTrue(PrivateInputPolicy.isPasswordField(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD));
        assertTrue(PrivateInputPolicy.isPasswordField(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD));
        assertTrue(PrivateInputPolicy.isPasswordField(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD));
        assertTrue(PrivateInputPolicy.isPasswordField(
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD));
    }

    @Test
    public void rejectsOrdinaryAndNonPasswordVariations() {
        assertFalse(PrivateInputPolicy.isPasswordField(InputType.TYPE_CLASS_TEXT));
        assertFalse(PrivateInputPolicy.isPasswordField(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS));
        assertFalse(PrivateInputPolicy.isPasswordField(InputType.TYPE_CLASS_NUMBER));
        assertFalse(PrivateInputPolicy.isPasswordField(InputType.TYPE_CLASS_PHONE));
        assertFalse(PrivateInputPolicy.isPasswordField(InputType.TYPE_NULL));
    }

    @Test
    public void noPersonalizedLearningMakesAnyFieldPrivate() {
        assertTrue(PrivateInputPolicy.isPrivateField(
                InputType.TYPE_CLASS_TEXT, EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING));
        assertTrue(PrivateInputPolicy.isPrivateField(
                InputType.TYPE_CLASS_NUMBER, EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING));
        assertFalse(PrivateInputPolicy.isPrivateField(InputType.TYPE_CLASS_TEXT, 0));
    }

    @Test
    public void onlyNumericClassesUseDirectDigits() {
        assertTrue(PrivateInputPolicy.usesDirectDigits(
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD));
        assertTrue(PrivateInputPolicy.usesDirectDigits(InputType.TYPE_CLASS_PHONE));
        assertFalse(PrivateInputPolicy.usesDirectDigits(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD));
        assertFalse(PrivateInputPolicy.usesDirectDigits(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD));
        assertFalse(PrivateInputPolicy.usesDirectDigits(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD));
    }
}
