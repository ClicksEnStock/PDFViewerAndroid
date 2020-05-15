/* Copyright (c) 2019 Conceptgame */
package Extensions;

import Actions.CActExtension;
import Application.CRunApp;
import Banks.CImage;
import Conditions.CCndExtension;
import Expressions.CValue;
import OpenGL.GLRenderer;
import RunLoop.CCreateObjectInfo;
import RunLoop.CObjInfo;
import Runtime.MMFRuntime;
import Services.CBinaryFile;
import Runtime.Log;
import Extensions.CRunExtension;

import android.graphics.pdf.PdfRenderer;
import android.graphics.Bitmap;
import android.os.ParcelFileDescriptor;
import android.Manifest;
import java.io.IOException;
import java.util.HashMap;
import java.io.File;

public class CRunPDFViewer extends CRunExtension
{
	/**
     * File descriptor of the PDF.
     */
    private ParcelFileDescriptor mFileDescriptor;

    /**
     * {@link android.graphics.pdf.PdfRenderer} to render the PDF.
     */
    private PdfRenderer mPdfRenderer;

    /**
     * Page that is currently shown on the screen.
     */
    private PdfRenderer.Page mCurrentPage;
	private int mCurrentImageNumber;
	private String mFilename;
	private boolean visible;
	private boolean pdfloaded;
	private CImage mImage;
	private CValue expRet;
	private HashMap<String, String> permissionsApi23;
	private boolean enabled_perms;
	private String mLastError;
	private boolean mDebugLoggingEnabled;
	
	public CRunPDFViewer() {
		expRet = new CValue(0);
	}
	
	@Override 
	public int getNumberOfConditions()
	{
		return 1;
	}
	
    @Override
    public boolean createRunObject(CBinaryFile file, CCreateObjectInfo cob, int version)
    {
		pdfloaded = false;
		visible = true;
		mFilename = "";
		mCurrentImageNumber = -1;
		mLastError = "";
		
		int editorWidth = file.readShort();
        int editorHeight = file.readShort();
        ho.setWidth(editorWidth);
        ho.setHeight(editorHeight);
		mDebugLoggingEnabled = file.readShort() != 0;
		if(mDebugLoggingEnabled){
		Log.Log("object is : " + editorWidth + "x" + editorHeight + " big" );
		Log.Log("debug logging is : " + mDebugLoggingEnabled);
		}
		
		if(MMFRuntime.deviceApi > 22) {
			permissionsApi23 = new HashMap<String, String>();
			permissionsApi23.put(Manifest.permission.READ_EXTERNAL_STORAGE, "Read Storage");
			if(!MMFRuntime.inst.verifyOkPermissionsApi23(permissionsApi23)){
			}
			else{
				enabled_perms = true;
			}
		}
		else{
			enabled_perms = true;
		}		
        return false;
    }

    @Override
    public void displayRunObject()
    {
    	if (pdfloaded && visible)
    	{
    		if (mImage == null){
				mLastError = "Cannot render, image is null";
    		return;}

    		int drawX = ho.hoX;
    		int drawY = ho.hoY;

      		drawX -= rh.rhWindowX;
			drawY -= rh.rhWindowY;

			//mImage.setResampling(ho.bAntialias);
			if(mDebugLoggingEnabled){
			Log.Log("image is : " + mImage.getWidth() + ";" + mImage.getHeight() );
			Log.Log("render image at : " + drawX + ";" + drawY );
			}
			GLRenderer.inst.renderImage(mImage, drawX, drawY, -1, -1, 0, 0);
    	}
    }
    
    @Override
    public void pauseRunObject() { }
    
    @Override
    public void continueRunObject() {

    }
    
    /*@Override
    public void getZoneInfos()
    {
    	if (pdfloaded)
    	{
			if (mImage != null)
            {
			    ho.hoImgWidth = mImage.getWidth();
			    ho.hoImgHeight = mImage.getHeight();
            }
    	}
    	else
    	{
    		ho.hoImgWidth = 1;
    		ho.hoImgHeight = 1;
    	}
    }*/
    
    @Override
    public boolean condition (int num, CCndExtension cnd)
    {
    	switch (num)
    	{
    	case 0:
			return pdfloaded;  		
    	};
    	
    	return false;
    }
    
    @Override
    public void action (int num, CActExtension act)
    {
		try 
		{
			switch (num)
			{
				case 0:
				{
					String fileName = act.getParamFilename(rh, 0);
					mFilename = fileName;
					
						if(mDebugLoggingEnabled){
						Log.Log("Loading file: " + mFilename);
						}
						openRenderer(mFilename);
						if(pdfloaded)
						{
							if(mDebugLoggingEnabled){
							Log.Log("File loaded." + mFilename);
							}
							mCurrentImageNumber = 0;
							renderPage(0);
						}
						else
						{
							mLastError = "Cannot load file "+mFilename;
						}
					return;
				}
				case 1:
				{
					closeRenderer();
					return;
				}
				case 2:
				{
					int nimage = act.getParamExpression (rh, 0);
					if(mDebugLoggingEnabled){
					Log.Log("Set current page to " + nimage);
					}
					if (nimage >= 0 && mPdfRenderer!=null)
					{
						mCurrentImageNumber = nimage;
						renderPage(mCurrentImageNumber);
					}
					else
					{
						mLastError = "Cannot set current page";
					}
					
					return;
				}
			};
		} 
		catch (Exception e) {
			if(mDebugLoggingEnabled){
			Log.Log("exception caught by action:" + e.toString());
			}
			mLastError = e.toString();
		}    	
    }
    
    @Override
    public CValue expression (int num)
    {
    	switch (num)
    	{
	    	case 0:
			{
				String error = mLastError;
	    		expRet.forceString(error);
				return expRet;
			}
			case 1:
			{
				if(mPdfRenderer!=null)
				{
					expRet.forceInt(mPdfRenderer.getPageCount());
				}
				else{
					expRet.forceInt(0);
				}
	    		return expRet;
			}
			case 2:
			{
	    		expRet.forceInt(mCurrentImageNumber);
	    		return expRet;
			}
			
    	};   	
    	return expRet;
    }
	
	/**
     * Sets up a {@link android.graphics.pdf.PdfRenderer} and related resources.
     */
    private void openRenderer(String filename) throws IOException {
		if(filename != null && filename.length() > 0) 
		{
			pdfloaded = false;
			File file = new File(filename);
			if(mDebugLoggingEnabled){
			Log.Log("open pdf.");
			}
			mFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
			// This is the PdfRenderer we use to render the PDF.
			if (mFileDescriptor != null) {
				mPdfRenderer = new PdfRenderer(mFileDescriptor);
			}
			else
			{
				mLastError = "File Descriptor is null";
			}
			pdfloaded = true;
		}
		else
		{
			mLastError = "File name is empty or null";
		}
    }
	
	private void renderPage(int index) {

		if (index >= mPdfRenderer.getPageCount()) {
            mLastError = "index bigger than number of pages in pdf";
			return;
        }

        // Make sure to close the current page before opening another one.
        if (null != mCurrentPage) {
            mCurrentPage.close();
        }
        
		// Use `openPage` to open a specific page in PDF.
		if(mDebugLoggingEnabled){
		Log.Log("opening pdf page...");
		}
		mCurrentPage = mPdfRenderer.openPage(index);
		// Important: the destination bitmap must be ARGB (not RGB).
		if(mDebugLoggingEnabled){
		Log.Log("creating bitmap...");
		}
		
		int pageWidth = mCurrentPage.getWidth();
        int pageHeight = mCurrentPage.getHeight();
        float scale = Math.min((float) ho.hoImgWidth / pageWidth, (float) ho.hoImgHeight / pageHeight);
        Bitmap bitmap = Bitmap.createBitmap((int) (pageWidth * scale), (int) (pageHeight * scale), Bitmap.Config.ARGB_8888);
		//Bitmap bitmap = Bitmap.createBitmap(mCurrentPage.getWidth(), mCurrentPage.getHeight(),Bitmap.Config.ARGB_8888);
		// Here, we render the page onto a Bitmap.
		// To render a portion of the page, use the second and third parameter. Pass nulls to get the default result.
		if(mDebugLoggingEnabled){
		Log.Log("redering pdf page...");
		}
		mCurrentPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
		// Create image
		if(bitmap != null)
		{
			Log.Log("Creating image");
			// Create image
			int xHS = 0;
			int yHS = 0;
			int xAP = 0;
			int yAP = 0;
			short newImg = this.rh.rhApp.imageBank.addImage(bitmap, (short) xHS, (short) yHS, (short) xAP, (short) yAP, true);

			if(newImg!=-1)
			{
				mImage = this.ho.getImageBank().getImageFromHandle(newImg);
			}
		}
		else
		{
			mLastError = "Page cannot be rendered, bitmap is null";
			if(mDebugLoggingEnabled){
			Log.Log("Page cannot be rendered, bitmap is null");
			}
		}
    }

    /**
     * Closes the {@link android.graphics.pdf.PdfRenderer} and related resources.
     *
     * @throws java.io.IOException When the PDF file cannot be closed.
     */
    private void closeRenderer() throws IOException {
        if (null != mCurrentPage) {
            mCurrentPage.close();
        }
		if(mPdfRenderer!=null)	{
			mPdfRenderer.close();
		}
		if(mFileDescriptor!=null)	{
			mFileDescriptor.close();
		}
    }
}
