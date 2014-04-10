/*
 * Copyright 2013 Megion Research and Development GmbH
 *
 * Licensed under the Microsoft Reference Source License (MS-RSL)
 *
 * This license governs use of the accompanying software. If you use the software, you accept this license.
 * If you do not accept the license, do not use the software.
 *
 * 1. Definitions
 * The terms "reproduce," "reproduction," and "distribution" have the same meaning here as under U.S. copyright law.
 * "You" means the licensee of the software.
 * "Your company" means the company you worked for when you downloaded the software.
 * "Reference use" means use of the software within your company as a reference, in read only form, for the sole purposes
 * of debugging your products, maintaining your products, or enhancing the interoperability of your products with the
 * software, and specifically excludes the right to distribute the software outside of your company.
 * "Licensed patents" means any Licensor patent claims which read directly on the software as distributed by the Licensor
 * under this license.
 *
 * 2. Grant of Rights
 * (A) Copyright Grant- Subject to the terms of this license, the Licensor grants you a non-transferable, non-exclusive,
 * worldwide, royalty-free copyright license to reproduce the software for reference use.
 * (B) Patent Grant- Subject to the terms of this license, the Licensor grants you a non-transferable, non-exclusive,
 * worldwide, royalty-free patent license under licensed patents for reference use.
 *
 * 3. Limitations
 * (A) No Trademark License- This license does not grant you any rights to use the Licensor’s name, logo, or trademarks.
 * (B) If you begin patent litigation against the Licensor over patents that you think may apply to the software
 * (including a cross-claim or counterclaim in a lawsuit), your license to the software ends automatically.
 * (C) The software is licensed "as-is." You bear the risk of using it. The Licensor gives no express warranties,
 * guarantees or conditions. You may have additional consumer rights under your local laws which this license cannot
 * change. To the extent permitted under your local laws, the Licensor excludes the implied warranties of merchantability,
 * fitness for a particular purpose and non-infringement.
 */

package com.mycelium.wallet.lt.activity;

import java.util.UUID;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

import com.mycelium.lt.api.LtApi;
import com.mycelium.wallet.MbwManager;
import com.mycelium.wallet.R;
import com.mycelium.wallet.Utils;
import com.mycelium.wallet.lt.LocalTraderEventSubscriber;
import com.mycelium.wallet.lt.LocalTraderManager;
import com.mycelium.wallet.lt.activity.sell.CreateOrEditSellOrderActivity;
import com.mycelium.wallet.lt.api.CreateInstantBuyOrder;
import com.mycelium.wallet.lt.api.CreateSellOrder;
import com.mycelium.wallet.lt.api.EditSellOrder;
import com.mycelium.wallet.lt.api.Request;

public class SendRequestActivity extends Activity {

   private static final int CREATE_TRADER_RESULT_CODE = 1;
   private static final int SOLVE_CAPTCHA_RESULT_CODE = 2;
   private MbwManager _mbwManager;
   private LocalTraderManager _ltManager;
   private Request _request;
   private boolean _requestSent;
   private boolean _isCaptchaSolved;

   public static void callMe(Activity currentActivity, Request request, String title) {
      Intent intent = new Intent(currentActivity, SendRequestActivity.class);
      intent.putExtra("request", request);
      intent.putExtra("title", title);
      currentActivity.startActivity(intent);
   }

   @Override
   public void onCreate(Bundle savedInstanceState) {
      this.requestWindowFeature(Window.FEATURE_NO_TITLE);
      super.onCreate(savedInstanceState);
      setContentView(R.layout.lt_send_request_activity);
      _mbwManager = MbwManager.getInstance(this.getApplication());
      _ltManager = _mbwManager.getLocalTraderManager();

      _request = (Request) getIntent().getSerializableExtra("request");
      String title = getIntent().getStringExtra("title");
      ((TextView) findViewById(R.id.tvTitle)).setText(title);
      if (savedInstanceState != null) {
         _requestSent = savedInstanceState.getBoolean("relquestSent");
         _isCaptchaSolved = savedInstanceState.getBoolean("isCaptchaSolved");
      }
   }

   @Override
   protected void onResume() {
      _ltManager.subscribe(ltSubscriber);
      if (!_requestSent) {
         if (_ltManager.hasLocalTraderAccount()) {
            // We have a Local Trader account
            makeRequest();
         } else {
            // We don't have a Local Trader account, create one
            CreateTrader1Activity.callMe(this, CREATE_TRADER_RESULT_CODE);
         }
      }
      super.onResume();
   }

   private void makeRequest() {
      if (_ltManager.isCaptchaRequired(_request) && !_isCaptchaSolved) {
         SolveCaptchaActivity.callMe(this, SOLVE_CAPTCHA_RESULT_CODE);
      } else {
         _requestSent = true;
         _ltManager.makeRequest(_request);
      }
   }

   @Override
   protected void onPause() {
      _ltManager.unsubscribe(ltSubscriber);
      super.onPause();
   }

   @Override
   protected void onSaveInstanceState(Bundle outState) {
      outState.putBoolean("requestSent", _requestSent);
      outState.putBoolean("isCaptchaSolved", _isCaptchaSolved);
      super.onSaveInstanceState(outState);
   }

   public void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {
      if (requestCode == CREATE_TRADER_RESULT_CODE) {
         if (resultCode == RESULT_OK) {
            // great, we will try and create the instant buy order on resume
         } else {
            // Creation failed, bail out
            finish();
         }
      } else if (requestCode == SOLVE_CAPTCHA_RESULT_CODE) {
         if (resultCode == RESULT_OK) {
            // great, we will try and create the instant buy order on resume
            _isCaptchaSolved = true;
         } else {
            // User aborted captcha, bail out
            finish();
         }
      }
   }

   private LocalTraderEventSubscriber ltSubscriber = new LocalTraderEventSubscriber(new Handler()) {

      @Override
      public void onLtError(int errorCode) {
         if (errorCode == LtApi.ERROR_CODE_CANNOT_TRADE_WITH_SELF) {
            // You cannot trade with yourself
            Toast.makeText(SendRequestActivity.this, R.string.lt_error_cannot_trade_with_self, Toast.LENGTH_LONG)
                  .show();
         } else {
            // Some other error
            Toast.makeText(SendRequestActivity.this, R.string.lt_error_api_occurred, Toast.LENGTH_LONG).show();
         }
         finish();
      }

      @Override
      public boolean onNoLtConnection() {
         Utils.toastConnectionError(SendRequestActivity.this);
         finish();
         return true;
      };

      @Override
      public void onLtInstantBuyOrderCreated(UUID id, CreateInstantBuyOrder request) {
         TradeActivity.callMe(SendRequestActivity.this, id);
         finish();
      };

      @Override
      public void onLtSellOrderCreated(UUID sellOrderId, CreateSellOrder request) {
         Toast.makeText(SendRequestActivity.this, R.string.lt_sell_order_created, Toast.LENGTH_LONG).show();
         finish();
      }

      public void onLtSellOrderEdited(EditSellOrder request) {
         Toast.makeText(SendRequestActivity.this, R.string.lt_sell_order_edited, Toast.LENGTH_LONG).show();
         finish();
      };

      public void onLtSellOrderRetrieved(com.mycelium.lt.api.model.SellOrder sellOrder,
            com.mycelium.wallet.lt.api.GetSellOrder request) {
         CreateOrEditSellOrderActivity.callMe(SendRequestActivity.this, sellOrder);
         finish();
      };

   };

}