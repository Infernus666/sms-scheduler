/**
 * Copyright (c) 2012 Vinayak Solutions Private Limited 
 * See the file license.txt for copying permission.
*/

package com.vinsol.sms_scheduler.activities;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;

import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Groups;
import android.text.method.ScrollingMovementMethod;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.ExpandableListContextMenuInfo;
import android.widget.ExpandableListView.OnChildClickListener;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.vinsol.sms_scheduler.Constants;
import com.vinsol.sms_scheduler.DBAdapter;
import com.vinsol.sms_scheduler.R;
import com.vinsol.sms_scheduler.models.Contact;
import com.vinsol.sms_scheduler.models.Recipient;
import com.vinsol.sms_scheduler.models.Sms;
import com.vinsol.sms_scheduler.utils.Log;
import com.vinsol.sms_scheduler.SmsSchedulerApplication;

public class Home extends Activity {
    
	private ArrayList<Sms> scheduledSMSs = new ArrayList<Sms>();
	private ArrayList<Sms> sentSMSs = new ArrayList<Sms>();
	private ArrayList<Sms> drafts = new ArrayList<Sms>();
	
	private ExpandableListView 		explList;
	private ImageView				newSmsButton;
	private ImageView				optionsImageButton;
	
	private LinearLayout blankListLayout;
	
	private Button blankListAddButton;
	
	private SimpleExpandableListAdapter mAdapter;
	private ArrayList<HashMap<String, String>> headerData;
	private ArrayList<ArrayList<HashMap<String, Object>>> childData = new ArrayList<ArrayList<HashMap<String, Object>>>();
	
	private String[] numbersForSentDialog = new String[]{};
	private int smsPositionForSentDialog;
	
	private DBAdapter mdba = new DBAdapter(Home.this);
	
	private final String NAME = "name";
	private final String IMAGE = "image";
	private final String DATE = "date";
	private final String EXTRA_RECEIVERS = "ext_receivers";
	private final String RECEIVER = "receiver";
	
	private final int MENU_DELETE =	R.id.home_options_delete;
	
	private Dialog sentInfoDialog;
	
	private Dialog dataLoadWaitDialog;
	private int toOpen = 0;
	
	Long selectedSms;
	
	private Cursor groupCursor;

	private IntentFilter mIntentFilter;
	private IntentFilter dataloadIntentFilter;
	
	private BroadcastReceiver mUpdateReceiver = new BroadcastReceiver() {
		
		@Override
		public void onReceive(Context context, Intent intent) {
			loadData();
			mAdapter.notifyDataSetChanged();
		}
	};
	
	
	
	private BroadcastReceiver mDataLoadedReceiver = new BroadcastReceiver() {
		
		@Override
		public void onReceive(Context context, Intent intent) {
			if(dataLoadWaitDialog.isShowing()){
				dataLoadWaitDialog.cancel();
				if(toOpen == 1){
					toOpen = 0;
					intent = new Intent(Home.this, ManageGroups.class);
                    startActivity(intent);
				}
			}
		}
	};
	
	
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.home);
        
        if(!SmsSchedulerApplication.isDataLoaded){
        	ContactsAsync contactsAsync = new ContactsAsync();
    		contactsAsync.execute();
        }
        
        newSmsButton 		= (ImageView) findViewById(R.id.main_new_sms_imgbutton);
        explList 	 		= (ExpandableListView) findViewById(R.id.main_expandable_list);
        optionsImageButton 	= (ImageView) findViewById(R.id.main_options_menu_imgbutton);
        blankListLayout		= (LinearLayout) findViewById(R.id.blank_list_layout);
        blankListAddButton	= (Button) findViewById(R.id.blank_list_add_button);
        
        registerForContextMenu(explList);
        
        dataLoadWaitDialog = new Dialog(Home.this);
		dataLoadWaitDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        
        newSmsButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View arg0) {
				Intent intent = new Intent(Home.this, ScheduleNewSms.class);
				startActivity(intent);
			}
		});
        
        
        blankListAddButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View arg0) {
				Intent intent = new Intent(Home.this, ScheduleNewSms.class);
				startActivity(intent);
			}
		});
        
        
        explList.setOnChildClickListener(new OnChildClickListener() {
			
			@Override
			public boolean onChildClick(ExpandableListView arg0, View view, int groupPosition, int childPosition, long id) {
				if(groupPosition == 1){
					Intent intent = new Intent(Home.this, EditScheduledSms.class);
					intent.putExtra("SMS DATA", scheduledSMSs.get(childPosition));
					startActivity(intent);
				}else if(groupPosition == 2){
					showSentInfoDialog(childPosition);
				}else if(groupPosition == 0){
					Intent intent = new Intent(Home.this, EditScheduledSms.class);
					intent.putExtra("SMS DATA", drafts.get(childPosition));
					startActivity(intent);
				}
				return false;
			}
		});
	    
        
        
        optionsImageButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				openOptionsMenu();
			}
		});
        
        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(getResources().getString(R.string.update_action));
        
        dataloadIntentFilter = new IntentFilter();

        dataloadIntentFilter.addAction(Constants.DIALOG_CONTROL_ACTION);

        setExplData();
        
        explList.setAdapter(mAdapter);
        registerForContextMenu(explList);
    }
    
    
    
    
    @Override
    protected void onResume() {
    	super.onResume();
    	
    	doScreenUpdate();
    	setExplData();
    	explList.setAdapter(mAdapter);
    	explList.expandGroup(0);
    	explList.expandGroup(1);
    	explList.expandGroup(2);
    	
    	registerReceiver(mUpdateReceiver, mIntentFilter);
    	registerReceiver(mDataLoadedReceiver, dataloadIntentFilter);
    }
    
    
    
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
    	super.onCreateContextMenu(menu, v, menuInfo);
    	
    	ExpandableListView.ExpandableListContextMenuInfo info = (ExpandableListView.ExpandableListContextMenuInfo) menuInfo;
		int type  = ExpandableListView.getPackedPositionType (info.packedPosition);
		ExpandableListView.getPackedPositionGroup(info.packedPosition);
		ExpandableListView.getPackedPositionChild(info.packedPosition);
		
		if(type == 1){
			final String MENU_TITLE_DELETE = "Delete";
			CharSequence menu_title = MENU_TITLE_DELETE.subSequence(0, MENU_TITLE_DELETE.length());
			menu.add(0, MENU_DELETE, 1, menu_title);
		}
    }
    
    
    
    
    @Override
	public boolean onContextItemSelected(MenuItem item) {
		ExpandableListContextMenuInfo info = (ExpandableListContextMenuInfo) item.getMenuInfo();
		int groupPos = 0, childPos = 0;
		int type = ExpandableListView.getPackedPositionType(info.packedPosition);
		if (type == ExpandableListView.PACKED_POSITION_TYPE_CHILD) {
			groupPos = ExpandableListView.getPackedPositionGroup(info.packedPosition);
			childPos = ExpandableListView.getPackedPositionChild(info.packedPosition);
			
			switch (item.getItemId()) {
				case MENU_DELETE:
					//--------------------------------------Delete context option ------------------------------------
					mdba.open();
					
					if(groupPos == 1){
						selectedSms = scheduledSMSs.get(childPos).keyId;
					}else if(groupPos == 2){
						selectedSms = sentSMSs.get(childPos).keyId;
					}else if(groupPos == 0){
						selectedSms = drafts.get(childPos).keyId;
					}
					deleteSms();
			        
			        break;
					//--------------------------------------------------------------------------------------------------
			}
		}
		return super.onContextItemSelected(item);
	}
    
    
    
    
    
    private void setExplData(){
    	loadData();
    	
    	final LayoutInflater layoutInflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    	mAdapter = new SimpleExpandableListAdapter(
    	    	this,
    	    	headerData,
    	    	android.R.layout.simple_expandable_list_item_1,
    	    	new String[] { NAME },
    	    	new int[] { android.R.id.text1 },
    	    	childData,
    	    	0,
    	    	null,
    	    	new int[] {}
    	){
    		@Override
			public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
    			GroupListHolder holder;
    			if(convertView == null) {
    				LayoutInflater li = getLayoutInflater();
        			convertView = li.inflate(R.layout.home_expandable_list_group, null);
        			holder = new GroupListHolder();
        			holder.groupHeading = (TextView) convertView.findViewById(R.id.group_heading);
        			convertView.setTag(holder);
    			}else{
    				holder = (GroupListHolder) convertView.getTag();
    			}
    			
    			holder.groupHeading.setText(headerData.get(groupPosition).get(NAME));
    			
    			return convertView;
    		}


			@Override
    		public android.view.View getChildView(int groupPosition, final int childPosition, boolean isLastChild, android.view.View convertView, android.view.ViewGroup parent) {
				ChildRowHolder holder;
				if(convertView==null){
					convertView = layoutInflater.inflate(R.layout.home_expandable_list_child, null, false);
					holder = new ChildRowHolder();
					holder.messageTextView  		= (TextView)  convertView.findViewById(R.id.main_row_message_area);
	    			holder.statusImageView 			= (ImageView) convertView.findViewById(R.id.main_row_image_area);
	    			holder.dateTextView				= (TextView)  convertView.findViewById(R.id.main_row_date_area);
	    			holder.receiverTextView 		= (TextView)  convertView.findViewById(R.id.main_row_recepient_area);
	    			holder.extraReceiversTextView 	= (TextView)  convertView.findViewById(R.id.main_row_extra_recepient_area);
	    			convertView.setTag(holder);
				}else{
					holder = (ChildRowHolder) convertView.getTag();
				}
				
    			if(groupPosition == 1) {
    				holder.messageTextView.setText(scheduledSMSs.get(childPosition).keyMessage);
    				holder.statusImageView.setImageResource(scheduledSMSs.get(childPosition).keyImageRes);
    				holder.dateTextView.setText(scheduledSMSs.get(childPosition).keyDate);
    				holder.receiverTextView.setText(numbersLengthRectify(scheduledSMSs.get(childPosition).keyNumber));
    				holder.extraReceiversTextView.setText(extraReceiversCal(scheduledSMSs.get(childPosition).keyNumber));
    				holder.messageTextView.setTextColor(getResources().getColor(R.color.black));
    				holder.receiverTextView.setTextColor(getResources().getColor(R.color.black));
    			} else if(groupPosition == 2) {
    				holder.messageTextView.setText(sentSMSs.get(childPosition).keyMessage);
    				holder.statusImageView.setImageResource(sentSMSs.get(childPosition).keyImageRes);
    				holder.dateTextView.setText(sentSMSs.get(childPosition).keyDate);
    				holder.receiverTextView.setText(numbersLengthRectify(sentSMSs.get(childPosition).keyNumber));
    				holder.extraReceiversTextView.setText(extraReceiversCal(sentSMSs.get(childPosition).keyNumber));
    				holder.messageTextView.setTextColor(getResources().getColor(R.color.black));
    				holder.receiverTextView.setTextColor(getResources().getColor(R.color.black));
    			} else if(groupPosition == 0){
    				if(!drafts.get(childPosition).keyMessage.matches("^(''|[' ']*)$")){
    					holder.messageTextView.setText(drafts.get(childPosition).keyMessage);
    				}else{
    					holder.messageTextView.setText("[No Message Written]");
    					holder.messageTextView.setTextColor(getResources().getColor(R.color.grey));
    				}
    				holder.statusImageView.setImageResource(drafts.get(childPosition).keyImageRes);
    				holder.dateTextView.setText(drafts.get(childPosition).keyDate);
    				if(!drafts.get(childPosition).keyNumber.matches("^(''|[' ']*)$")){
    					holder.receiverTextView.setText(numbersLengthRectify(drafts.get(childPosition).keyNumber));
        				holder.extraReceiversTextView.setText(extraReceiversCal(drafts.get(childPosition).keyNumber));
    				}else{
    					holder.receiverTextView.setText("[No Recepients Added]");
    					holder.receiverTextView.setTextColor(getResources().getColor(R.color.grey));
    					holder.extraReceiversTextView.setText("");
    				}
    			}
    			
    			
    			
    			
    			if(groupPosition == 1){
    				holder.statusImageView.setOnClickListener(new OnClickListener() {
						
						@Override
						public void onClick(View v) {
							showDeleteDialog(scheduledSMSs, childPosition, "Delete this Scheduled Message?");
						}
					});
    				
    			}else if(groupPosition == 0){
    				holder.statusImageView.setOnClickListener(new OnClickListener() {
						
						@Override
						public void onClick(View v) {
							showDeleteDialog(drafts, childPosition, "Delete this Draft?");
						}
    				});
    			}
    			return convertView;
			}
    	};
    }
    
    
    private void loadData(){
    	
    	Log.d("Into LoadData");
    	childData.clear();
    	
    	mdba.open();
    	drafts.clear();
    	scheduledSMSs.clear();
    	sentSMSs.clear();
    	
    	
    	//-----------------------Putting group headers for Expandable list---------------------------- 
    	headerData = new ArrayList<HashMap<String, String>>();
    	
//    	if(draftCur.getCount()>0){
    		HashMap<String, String> group3 = new HashMap<String, String>();
        	group3.put(NAME, "Drafts");
        	headerData.add(group3);
//    	}
    	
//    	if(schCur.getCount()>0){
    		HashMap<String, String> group1 = new HashMap<String, String>();
        	group1.put(NAME, "Scheduled");
        	headerData.add(group1);
//    	}
    	
//    	if(sentCur.getCount()>0){
    		HashMap<String, String> group2 = new HashMap<String, String>();
        	group2.put(NAME, "Sent");
        	headerData.add(group2);
//    	}
    	//---------------------------------------------------------------------------------------------
    	
        
    	
    	//--------------Extracting Sent, Draft and Scheduled messages from Database------------------------
        Cursor SMSsCur = mdba.fetchAllRecipientDetails();
        
        long previousSmsId = -1;
        int previousSmsType = -1;
        	
        Sms SMS = new Sms();
        Log.d("size of SMSsCur : " + SMSsCur.getCount());
        if(SMSsCur.moveToFirst()){
        	do{
        		if(previousSmsId!=SMSsCur.getLong(SMSsCur.getColumnIndex(DBAdapter.KEY_ID))){
        			
        			if(previousSmsType == 0){
        				drafts.add(SMS);
        			}else if(previousSmsType == 1 || previousSmsType == 3){
        				scheduledSMSs.add(SMS);
        			}else if(previousSmsType == 2){
        				sentSMSs.add(SMS);
        			}
        			
        			previousSmsId = SMSsCur.getLong(SMSsCur.getColumnIndex(DBAdapter.KEY_ID));
        			previousSmsType = SMSsCur.getInt(SMSsCur.getColumnIndex(DBAdapter.KEY_STATUS));
        			
        			String displayName = "";
        			ArrayList<Recipient> tempRecipients = new ArrayList<Recipient>();
    				
    				SMS = new Sms(SMSsCur.getLong(SMSsCur.getColumnIndex(DBAdapter.KEY_ID)),
    						displayName,
    						SMSsCur.getString(SMSsCur.getColumnIndex(DBAdapter.KEY_MESSAGE)),
    						SMSsCur.getInt(SMSsCur.getColumnIndex(DBAdapter.KEY_MSG_PARTS)),
    						SMSsCur.getLong	(SMSsCur.getColumnIndex(DBAdapter.KEY_TIME_MILLIS)),
    						SMSsCur.getString(SMSsCur.getColumnIndex(DBAdapter.KEY_DATE)),
    						tempRecipients);
        		}
        		Recipient recipient = new Recipient(SMSsCur.getLong(SMSsCur.getColumnIndex(DBAdapter.KEY_RECIPIENT_ID)),
        											SMSsCur.getInt(SMSsCur.getColumnIndex(DBAdapter.KEY_RECIPIENT_TYPE)),
        											SMSsCur.getString(SMSsCur.getColumnIndex(DBAdapter.KEY_DISPLAY_NAME)),
        											SMSsCur.getLong(SMSsCur.getColumnIndex(DBAdapter.KEY_CONTACT_ID)),
        											SMSsCur.getLong(SMSsCur.getColumnIndex(DBAdapter.KEY_ID)),
        											SMSsCur.getInt(SMSsCur.getColumnIndex(DBAdapter.KEY_SENT)),
        											SMSsCur.getInt(SMSsCur.getColumnIndex(DBAdapter.KEY_DELIVER)));
        		if(SMS.keyNumber.equals("")){
        			SMS.keyNumber = SMSsCur.getString(SMSsCur.getColumnIndex(DBAdapter.KEY_DISPLAY_NAME));
        		}else{
        			SMS.keyNumber = SMS.keyNumber + ", " + SMSsCur.getString(SMSsCur.getColumnIndex(DBAdapter.KEY_DISPLAY_NAME));
        		}
        		
    			SMS.keyRecipients.add(recipient);
        		
        	}while(SMSsCur.moveToNext());
        	
        	if(previousSmsType == 0){
				drafts.add(SMS);
			}else if(previousSmsType == 1 || previousSmsType == 3){
				Log.d("getting added to schedules");
				scheduledSMSs.add(SMS);
			}else if(previousSmsType == 2){
				sentSMSs.add(SMS);
			}
        }
        SMSsCur.close();
        //------------------------------------------------Messages Extracted From Database---------------------
        
        
        
        //------------------------Loading scheduled msgs----------------------------------------------------
    	ArrayList<HashMap<String, Object>> groupChildSch = new ArrayList<HashMap<String, Object>>();
    	
    	for(int i = 0; i< scheduledSMSs.size(); i++){
    		HashMap<String, Object> child = new HashMap<String, Object>();
    		child.put(NAME, scheduledSMSs.get(i).keyMessage);
    		scheduledSMSs.get(i).keyImageRes = R.drawable.delete_icon_states;
    		child.put(IMAGE, this.getResources().getDrawable(R.drawable.icon));
    		child.put(DATE, scheduledSMSs.get(i).keyDate);
    		child.put(RECEIVER, scheduledSMSs.get(i).keyNumber);
    		child.put(EXTRA_RECEIVERS, extraReceiversCal(scheduledSMSs.get(i).keyNumber));
    		groupChildSch.add(child);
    	}
    	//-------------------------------------------------------------------------end of scheduled msgs load-------- 
    	
    	
    	
    	
    	//--------------------------loading sent messages------------------------------------------
    	ArrayList<HashMap<String, Object>> groupChildSent = new ArrayList<HashMap<String, Object>>();

    	for(int i = sentSMSs.size()-1; i > -1; i--){
    		HashMap<String, Object> child = new HashMap<String, Object>();
    		child.put(NAME, sentSMSs.get(i).keyMessage);
    		int condition = 1;
    		
    		for(int k = 0; k< sentSMSs.get(i).keyRecipients.size(); k++){
    			if(sentSMSs.get(i).keyRecipients.get(k).sent == 0){
    				condition = 1;
    				sentSMSs.get(i).keyImageRes = R.drawable.sent_failure_icon;
    				break;
    			}
    			if(sentSMSs.get(i).keyRecipients.get(k).sent > 0 && !mdba.checkDelivery(sentSMSs.get(i).keyRecipients.get(k).recipientId)){
    				condition = 2;
    				sentSMSs.get(i).keyImageRes = R.drawable.sending_sms_icon;
    				break;
    			}
    			if(sentSMSs.get(i).keyRecipients.get(k).sent == sentSMSs.get(i).keyRecipients.get(k).delivered){
    				condition = 3;
    			}
    		}
    		
    		if(condition==3){
    			sentSMSs.get(i).keyImageRes = R.drawable.sent_success_icon;
    		}
    		
    		child.put(IMAGE, this.getResources().getDrawable(R.drawable.icon));
    		child.put(DATE, sentSMSs.get(i).keyDate);
    		
    		child.put(RECEIVER, numbersLengthRectify(sentSMSs.get(i).keyNumber));
    		child.put(EXTRA_RECEIVERS, extraReceiversCal(sentSMSs.get(i).keyNumber));
    		groupChildSent.add(child);
    	}
    	//--------------------------------------------------------------------------end of sent msgs load-----------
    	
    	
    	
    	//------------------------Loading Drafts----------------------------------------------------
    	ArrayList<HashMap<String, Object>> groupChildDraft = new ArrayList<HashMap<String, Object>>();

    	for(int i = 0; i< drafts.size(); i++){
    		HashMap<String, Object> child = new HashMap<String, Object>();
    		child.put(NAME, drafts.get(i).keyMessage);
    		drafts.get(i).keyImageRes = R.drawable.delete_icon_states;
    		
    		child.put(IMAGE, this.getResources().getDrawable(R.drawable.icon));
    		child.put(DATE, drafts.get(i).keyDate);
    		child.put(RECEIVER, numbersLengthRectify(drafts.get(i).keyNumber));
    		try{
    			child.put(EXTRA_RECEIVERS, extraReceiversCal(sentSMSs.get(i).keyNumber));
    		}catch (IndexOutOfBoundsException e) {
    			child.put(EXTRA_RECEIVERS, "");
			}
    		
    		groupChildDraft.add(child);
    	}
    	
    	childData.add(groupChildDraft);
    	childData.add(groupChildSch);
    	childData.add(groupChildSent);
    	//-------------------------------------------------------------------------end of drafts load--------
    	
    	mdba.close();
    }

	
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.options_menu, menu);
		return true;
	}
	
	
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
    	Intent intent;
		switch (item.getItemId()) {
	        case R.id.template_opt_menu:
	        	intent = new Intent(Home.this, ManageTemplates.class);
	        	startActivity(intent);
	            break;
	        case R.id.group_opt_menu:
	        	if(SmsSchedulerApplication.isDataLoaded){
	        		intent = new Intent(Home.this, ManageGroups.class);
		            startActivity(intent);
	        	}else{
	        		dataLoadWaitDialog.setContentView(R.layout.wait_dialog);
	        		toOpen = 1;
	        		dataLoadWaitDialog.show();
	        	}
	            break;
	    }
	    return true;
	}
	
	
	
	
	@Override
	protected void onPause() {
		super.onPause();
		unregisterReceiver(mUpdateReceiver);
		unregisterReceiver(mDataLoadedReceiver);
	}
	
	
	
	
	private void showSentInfoDialog(int childPos){
		sentInfoDialog = new Dialog(Home.this);
		sentInfoDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		sentInfoDialog.setContentView(R.layout.sent_sms_details);
		ListView numbersList = (ListView) sentInfoDialog.findViewById(R.id.sent_details_dialog_number_list);
		TextView timeLabel = (TextView) sentInfoDialog.findViewById(R.id.sent_details_dialog_time_label);
		TextView messageSpace = (TextView) sentInfoDialog.findViewById(R.id.sent_details_dialog_message_space);
		mdba.open();
		numbersForSentDialog = sentSMSs.get(childPos).keyNumber.split(", ");
		smsPositionForSentDialog = childPos;
		timeLabel.setText(sentSMSs.get(childPos).keyDate);
		messageSpace.setText(sentSMSs.get(childPos).keyMessage);
		messageSpace.setMovementMethod(new ScrollingMovementMethod());
		SentDialogNumberListAdapter sentDialogAdapter = new SentDialogNumberListAdapter();
		numbersList.setAdapter(sentDialogAdapter);
		mdba.close();
		sentInfoDialog.show();
	}
    
	
	
	
	//********* Adapter for the list of recipients and msg status, in the show dialog of sent msgs ***********************
	@SuppressWarnings("rawtypes")
	private class SentDialogNumberListAdapter extends ArrayAdapter{
		
		@SuppressWarnings({ "unchecked" })
		SentDialogNumberListAdapter(){
    		super(Home.this, R.layout.sent_sms_recepients_list_row, numbersForSentDialog);
    	}
    	
    	
    	@Override
    	public View getView(final int position, View convertView, ViewGroup parent) {
    		SentDialogListHolder holder;
    		if(convertView == null) {
    			LayoutInflater inflater = getLayoutInflater();
        		convertView = inflater.inflate(R.layout.sent_sms_recepients_list_row, parent, false);
        		holder = new SentDialogListHolder();
        		holder.numberLabel = (TextView)convertView.findViewById(R.id.sent_details_number_list_number_text);
        		holder.statusImage = (ImageView)convertView.findViewById(R.id.sent_details_number_list_status_image);
        		convertView.setTag(holder);
    		}else{
    			holder = (SentDialogListHolder) convertView.getTag();
    		}

    		holder.numberLabel.setText(numbersForSentDialog[position]);
    		
    		int condition = 1;
    		mdba.open();
			if(sentSMSs.get(smsPositionForSentDialog).keyRecipients.get(position).sent > 0 && (sentSMSs.get(smsPositionForSentDialog).keyRecipients.get(position).delivered != sentSMSs.get(smsPositionForSentDialog).keyMessageParts)){
				condition = 2;
			}else
			if(sentSMSs.get(smsPositionForSentDialog).keyRecipients.get(position).delivered == sentSMSs.get(smsPositionForSentDialog).keyMessageParts){
				condition = 3;
			}
			
			switch (condition) {
			case 1:
				holder.statusImage.setImageResource(R.drawable.sent_failure_icon);
				break;
				
			case 2:
				holder.statusImage.setImageResource(R.drawable.sending_sms_icon);
				break;
				
			case 3:
				holder.statusImage.setImageResource(R.drawable.sent_success_icon);
				break;
					
			default:
				break;
			}
    		mdba.close();
    		return convertView;
    	}
    }
	
	
	
	//------------------- For displaying appropriate number of recipients in sms listing---------------------
	
	private String numbersLengthRectify(String number){
		if(number.length()<= 30){
			return number;
		}
		int delimiterCount = 0;
		int validDelimiterCount = 0;
		int validLength = 0;
		for(int i = 0; i< number.length(); i++){
			if(number.charAt(i)==' ' && number.charAt(i-1)==','){
				delimiterCount++;
				if(i<=30){
					validDelimiterCount++;
					validLength = i;
				}
			}
		}
		String validLengthNumber = number.substring(0, validLength);
		
		return validLengthNumber;
	}
	
	
	
	private String extraReceiversCal(String number){
		if(number.length()<= 30){
			return "";
		}
		int delimiterCount = 0;
		int validDelimiterCount = 0;
		for(int i = 0; i< number.length(); i++){
			if(number.charAt(i)==' ' && number.charAt(i-1)==','){
				delimiterCount++;
				if(i<=30){
					validDelimiterCount++;
				}
			}
		}
		
		return "+" + (delimiterCount - validDelimiterCount + 1);
	}
	//----------------------------------------------------------------------------------------
	
	
	
	
	
	//------------------------Contacts Data Load functions---------------------------------------------
	public void loadContactsData(){
		if(SmsSchedulerApplication.contactsList.size()==0){
			System.currentTimeMillis();
			
			String[] projection = new String[] {Groups._ID};
			Uri groupsUri =  ContactsContract.Groups.CONTENT_URI;
			
			
			ContentResolver cr = getContentResolver();
			groupCursor = cr.query(groupsUri, projection, null, null, null);
		    Cursor cursor = cr.query(ContactsContract.Contacts.CONTENT_URI, null, null, null, null);
		    if(cursor.moveToFirst()){
		    	do{
		    	  if(!(cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)).equals("0"))){
		    		String id = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts._ID));
		    		Cursor phones = cr.query(Phone.CONTENT_URI, null, Phone.CONTACT_ID + " = " + id, null, null);
		    	    if(phones.moveToFirst()){
		    	    	Contact contact = new Contact();
			    		contact.content_uri_id = Long.parseLong(cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts._ID)));
			    		contact.name = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
			    		contact.number = phones.getString(phones.getColumnIndex(Phone.NUMBER));

		    	    	Cursor cur = cr.query(ContactsContract.Data.CONTENT_URI, new String[]{ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID}, ContactsContract.CommonDataKinds.GroupMembership.CONTACT_ID + "=" + contact.content_uri_id, null, null);
		    	    	if(cur.moveToFirst()){
		    	    		do{
		    	    			// SAZWQA: Should we add a rule that if GROUP_ROW_ID == 0 or it's equal to phone no. don't ADD it?
		    	    			if(!String.valueOf(cur.getLong(cur.getColumnIndex(ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID))).equals(contact.number) && cur.getLong(cur.getColumnIndex(ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID))!=0){
		    	    				boolean isValid = false;
		    	    				if(groupCursor.moveToFirst()){
		    	    					do{
		    	    						if(!cur.isClosed() && !groupCursor.isClosed() && cur.getLong(cur.getColumnIndex(ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID)) == groupCursor.getLong(groupCursor.getColumnIndex(Groups._ID))){
		    	    							isValid = true;
		    	    							break;
		    	    						}
		    	    					}while(groupCursor.moveToNext());
		    	    				}
		    	    				if(isValid){
		    	    					contact.groupRowId.add(cur.getLong(cur.getColumnIndex(ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID)));
			    	    			}
		    	    			}
		    	    		}while(cur.moveToNext());
		    	    	}
		    	    	cur.close();
		    	    	Uri uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contact.content_uri_id);
			    	    InputStream input = ContactsContract.Contacts.openContactPhotoInputStream(cr, uri);
			    	    try{
			    	    	contact.image = BitmapFactory.decodeStream(input);
			    	    	contact.image.getHeight();
			    	    } catch (NullPointerException e){
			    	    	contact.image = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.no_image_thumbnail);
			    	    }
			    	    
			    	    SmsSchedulerApplication.contactsList.add(contact);
		    	    }
		    	    phones.close();
		    	  }
		    	}while(cursor.moveToNext());
		    }
		    cursor.close();
		    groupCursor.close();
		}
	}
	
	
	private class ContactsAsync extends AsyncTask<Void, Void, Void>{

		@Override
		protected Void doInBackground(Void... params) {
			loadContactsData();
			return null;
		}
		
		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);
			
			SmsSchedulerApplication.isDataLoaded = true;
			Intent mIntent = new Intent();

			mIntent.setAction(Constants.DIALOG_CONTROL_ACTION);

			sendBroadcast(mIntent);
		}
	}
	
	
	private class GroupListHolder{
		TextView groupHeading;
	}
	
	
	private class ChildRowHolder{
		TextView messageTextView;
		ImageView statusImageView;
		TextView dateTextView;
		TextView receiverTextView;
		TextView extraReceiversTextView;
	}
	
	
	private class SentDialogListHolder{
		TextView numberLabel;
		ImageView statusImage;
	}
	
	
	private void deleteSms(){
		mdba.open();
		mdba.deleteSms(selectedSms, Home.this);
		Toast.makeText(Home.this, "Message Deleted", Toast.LENGTH_SHORT).show();
		loadData();
		mAdapter.notifyDataSetChanged();
		doScreenUpdate();
	}
	
	
	private void doScreenUpdate(){
		mdba.open();
		boolean ifSmsExist = mdba.ifSmsExist();
		mdba.close();
        if(ifSmsExist){
        	explList.setVisibility(LinearLayout.VISIBLE);
        	blankListLayout.setVisibility(LinearLayout.GONE);
        }else{
        	explList.setVisibility(LinearLayout.GONE);
            blankListLayout.setVisibility(LinearLayout.VISIBLE);
        }
	}
	
	
	public void showDeleteDialog(final ArrayList<Sms> SMSList, final int childPosition, String questionString){
		final Dialog d = new Dialog(Home.this);
		d.requestWindowFeature(Window.FEATURE_NO_TITLE);
		d.setContentView(R.layout.confirmation_dialog);
		TextView questionText 	= (TextView) 	d.findViewById(R.id.confirmation_dialog_text);
		Button yesButton 		= (Button) 		d.findViewById(R.id.confirmation_dialog_yes_button);
		Button noButton			= (Button) 		d.findViewById(R.id.confirmation_dialog_no_button);
		
		questionText.setText(questionString);
		
		yesButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				selectedSms = SMSList.get(childPosition).keyId;
				deleteSms();
		        d.cancel();
			}
		});
		
		noButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				d.cancel();
			}
		});
		d.show();
	}
}