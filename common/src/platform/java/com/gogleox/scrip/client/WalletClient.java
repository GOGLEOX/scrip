package com.gogleox.scrip.client;

import com.gogleox.scrip.Scrip;
import com.gogleox.scrip.account.AccountSnapshot;
import com.gogleox.scrip.account.TransactionRecord;
import com.gogleox.scrip.network.WalletProtocol;
import com.gogleox.scrip.network.WalletRequest;
import com.gogleox.scrip.network.WalletResponse;
import com.gogleox.scrip.network.WalletBranding;
import com.gogleox.scrip.organization.AccountAccess;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

/** Physical-client wallet presentation. It holds only permission-filtered server responses. */
public final class WalletClient {
    private static final String WAITING_FOR_SERVER = "Waiting for server response";
    private static final AtomicLong REQUEST_IDS = new AtomicLong();
    private static volatile Consumer<WalletRequest> sender = ignored -> { };
    private static volatile Optional<AccountSnapshot> selected = Optional.empty();
    private static volatile List<AccountAccess> accounts = List.of();
    private static volatile List<TransactionRecord> history = List.of();
    private static volatile Optional<com.gogleox.scrip.payment.PaymentPreview> preview = Optional.empty();
    private static volatile String status = "";
    private static volatile boolean hudEnabled;
    private static volatile boolean muted;
    private static volatile Theme theme=Theme.LIGHT;
    private static volatile HudAnchor hudAnchor=HudAnchor.TOP_LEFT;
    private static volatile float hudScale=1.0F;
    private static volatile int hudOpacityPercent=100;
    private static volatile HudTextColor hudNameColor=HudTextColor.THEME;
    private static volatile HudTextColor hudBalanceColor=HudTextColor.THEME;
    private static volatile String walletName="$crip";
    private static volatile Optional<ResourceLocation> walletIcon=Optional.empty();

    private WalletClient() { }
    public static void configure(Consumer<WalletRequest> value) { sender = value; }
    public static boolean isOpen() { return Minecraft.getInstance().screen instanceof WalletScreen; }
    public static void open() { Minecraft minecraft=Minecraft.getInstance(); if(minecraft.player!=null) { minecraft.setScreen(new WalletScreen()); requestOpen(); } }
    public static void toggle() { if(Minecraft.getInstance().screen instanceof WalletScreen) Minecraft.getInstance().setScreen(null); else open(); }
    public static void requestOpen() { send(WalletRequest.open(nextId())); }
    public static void receive(WalletResponse response) {
        response.account().ifPresent(value -> selected=Optional.of(value));
        response.preview().ifPresent(value -> preview=Optional.of(value));
        if (!response.accessibleAccounts().isEmpty()) accounts=response.accessibleAccounts();
        if (!response.history().isEmpty()) history=response.history();
        WalletBranding.fromWireValue(response.detail().orElse("")).ifPresent(branding->{walletName=branding.displayName();walletIcon=branding.iconTexture().flatMap(WalletClient::resource);});
        status=response.error().map(value -> "Server rejected request: "+value.name().toLowerCase().replace('_',' ')).orElseGet(() -> feedback(response));
        if(response.type()==WalletProtocol.Response.TRANSACTION_COMMITTED) preview=Optional.empty();
        if(response.type()==WalletProtocol.Response.ERROR_CODE) preview=Optional.empty();
        if("OPEN_WALLET".equals(response.detail().orElse(""))) open();
        if(response.type()==WalletProtocol.Response.TRANSACTION_COMMITTED && "EXECUTED".equals(response.detail().orElse("")) && !muted && Minecraft.getInstance().player!=null) Minecraft.getInstance().player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP,0.55F,1.1F);
    }
    private static String feedback(WalletResponse response) {
        String detail=response.detail().orElse("");
        return switch(response.type()) {
            case PREVIEW_RESULT -> "PREVIEW_READY".equals(detail)?"Review the payment details, then confirm.":detail.isBlank()?"":"Payment preview: "+detail.toLowerCase().replace('_',' ')+".";
            case TRANSACTION_COMMITTED -> "EXECUTED".equals(detail)?"Payment completed.":detail.isBlank()?"":"Payment: "+detail.toLowerCase().replace('_',' ')+".";
            case NOTIFICATION -> "OPEN_WALLET".equals(detail)?"":detail;
            default -> "";
        };
    }
    private static Optional<ResourceLocation> resource(String value){try{return Optional.of(ResourceLocation.parse(value));}catch(IllegalArgumentException ignored){return Optional.empty();}}
    public static void renderHud(GuiGraphics graphics) {
        if(!hudEnabled || selected.isEmpty()) return;
        AccountSnapshot account=selected.get();
        String identity=Minecraft.getInstance().player==null?"Wallet":Minecraft.getInstance().player.getGameProfile().getName();
        String header="Wallet: "+identity;
        String text=coinText(account.balance().units());
        Minecraft minecraft=Minecraft.getInstance();
        int contentWidth=Math.max(minecraft.font.width(header),minecraft.font.width(text))+12;
        int guiWidth=minecraft.getWindow().getGuiScaledWidth();
        int guiHeight=minecraft.getWindow().getGuiScaledHeight();
        int scaledWidth=Math.round(contentWidth*hudScale);
        int scaledHeight=Math.round(32*hudScale);
        int x=switch(hudAnchor){case TOP_LEFT,BOTTOM_LEFT->6;case TOP_RIGHT,BOTTOM_RIGHT->guiWidth-scaledWidth-6;};
        int y=switch(hudAnchor){case TOP_LEFT,TOP_RIGHT->6;case BOTTOM_LEFT,BOTTOM_RIGHT->guiHeight-scaledHeight-6;};
        graphics.pose().pushPose();
        graphics.pose().translate(x,y,0);
        graphics.pose().scale(hudScale,hudScale,1.0F);
        int alpha=Math.round(hudOpacityPercent*255F/100F);
        int panel=theme==Theme.DARK?0xFF121212:0xFFC6C6C6;
        int topEdge=theme==Theme.DARK?0xFF555555:0xFFFFFFFF;
        int bottomEdge=theme==Theme.DARK?0xFF111111:0xFF555555;
        int primary=theme==Theme.DARK?0xFFF8FAFC:0xFF404040;
        int secondary=theme==Theme.DARK?0xFFB8B8B8:0xFF666666;
        graphics.fill(0,0,contentWidth,32,(alpha<<24)|(panel&0xFFFFFF));
        graphics.fill(0,0,contentWidth,2,(alpha<<24)|(topEdge&0xFFFFFF));
        graphics.fill(0,0,2,32,(alpha<<24)|(topEdge&0xFFFFFF));
        graphics.fill(0,30,contentWidth,32,(alpha<<24)|(bottomEdge&0xFFFFFF));
        graphics.fill(contentWidth-2,0,contentWidth,32,(alpha<<24)|(bottomEdge&0xFFFFFF));
        graphics.drawString(minecraft.font,header,6,5,hudNameColor.resolve(secondary),false);
        graphics.drawString(minecraft.font,text,6,17,hudBalanceColor.resolve(primary),false);
        graphics.pose().popPose();
    }
    private static void send(WalletRequest request) { sender.accept(request); }
    private static long nextId() { return REQUEST_IDS.incrementAndGet(); }
    private static String coinText(long amount) { return String.format(Locale.ROOT,"%,d Coins",amount); }

    private static final class WalletScreen extends Screen {
        private static final int BLACK=0xFF121212, STEEL=0xFF2A2A2A, ORANGE=0xFFF97316, WHITE=0xFFF8FAFC;
        private Tab tab=Tab.OVERVIEW;
        private EditBox recipient;
        private EditBox amount;
        private boolean submitting;
        private boolean displayedConfirmation;
        private int historyOffset;
        private int offsetX;
        private int offsetY;
        private boolean dragging;
        private boolean resizing;
        private int dragPointerX;
        private int dragPointerY;
        private int resizeAnchorLeft;
        private int resizeAnchorTop;
        private int walletWidth=360;
        private int walletHeight=214;
        WalletScreen(){super(Component.literal("$crip Wallet"));}
        @Override protected void init(){
            int left=left(), top=top();
            int x=tabStart(left);
            for(Tab value:Tab.values()){Tab current=value;addRenderableWidget(Button.builder(Component.literal(value.label),button->{tab=current;rebuild();}).bounds(x,top+28,64,20).build());x+=68;}
            rebuild();
        }
        private void rebuild(){
            String recipientValue=recipient==null?"":recipient.getValue();
            String amountValue=amount==null?"":amount.getValue();
            displayedConfirmation=preview.isPresent()&&preview.get().confirmationRequired();
            clearWidgets(); int left=left(),top=top(),x=tabStart(left), action=actionY(); for(Tab value:Tab.values()){Tab current=value;addRenderableWidget(Button.builder(Component.literal(value.label),button->{tab=current;rebuild();}).bounds(x,top+28,64,20).build());x+=68;}addRenderableWidget(Button.builder(Component.literal("X"),button->onClose()).tooltip(Tooltip.create(Component.literal("Close wallet (B)."))).bounds(left+walletWidth()-26,top+4,14,16).build());
            if(tab==Tab.PAY){
                if(ultraCompact()){
                    recipient=new EditBox(font,left+12,top+52,156,18,Component.literal("Recipient player name"));recipient.setHint(Component.literal("Recipient"));recipient.setValue(recipientValue);
                    amount=new EditBox(font,left+172,top+52,64,18,Component.literal("Amount"));amount.setHint(Component.literal("Coins"));amount.setValue(amountValue);
                    addRenderableWidget(recipient);addRenderableWidget(amount);
                    boolean needsConfirmation=preview.isPresent()&&preview.get().confirmationRequired();
                    addRenderableWidget(Button.builder(Component.literal(needsConfirmation?"Confirm":"Preview"),button->{if(needsConfirmation)confirm();else preview();}).bounds(left+240,top+52,108,18).build());
                }else{
                    int field=compact()?62:82, second=compact()?84:106; recipient=new EditBox(font,left+18,top+field,walletWidth()-36,18,Component.literal("Recipient player name"));recipient.setHint(Component.literal("Recipient player name"));recipient.setValue(recipientValue); amount=new EditBox(font,left+18,top+second,walletWidth()-36,18,Component.literal("Amount"));amount.setHint(Component.literal("Whole Coins"));amount.setValue(amountValue);addRenderableWidget(recipient);addRenderableWidget(amount);addRenderableWidget(Button.builder(Component.literal("Preview payment"),button->preview()).bounds(left+18,action,140,20).build());if(displayedConfirmation)addRenderableWidget(Button.builder(Component.literal("Confirm payment"),button->confirm()).bounds(left+168,action,140,20).build());
                }
            }
            if(tab==Tab.HISTORY)addRenderableWidget(Button.builder(Component.literal("Refresh"),button->refreshHistory()).tooltip(Tooltip.create(Component.literal("Refresh transaction history."))).bounds(left+walletWidth()-84,top+54,66,20).build());
            if(tab==Tab.OVERVIEW){
                addRenderableWidget(Button.builder(Component.literal("Copy name"),button->copyPlayerName()).tooltip(Tooltip.create(Component.literal("Copies your verified player name for payments."))).bounds(left+18,action,80,20).build());
                addRenderableWidget(Button.builder(Component.literal(hudEnabled?"HUD on":"HUD off"),button->{hudEnabled=!hudEnabled;rebuild();}).tooltip(Tooltip.create(Component.literal("Shows or hides your local balance overlay."))).bounds(left+104,action,68,20).build());
                addRenderableWidget(Button.builder(Component.literal("⚙"),button->Minecraft.getInstance().setScreen(new HudSettingsScreen(this))).tooltip(Tooltip.create(Component.literal("Open HUD settings."))).bounds(left+176,action,16,20).build());
                addRenderableWidget(Button.builder(Component.literal(muted?"Sound off":"Sound on"),button->{muted=!muted;rebuild();}).tooltip(Tooltip.create(Component.literal("Toggles the sound played after a committed payment."))).bounds(left+198,action,66,20).build());
                addRenderableWidget(Button.builder(Component.literal(theme==Theme.DARK?"Light":"Dark"),button->{theme=theme==Theme.DARK?Theme.LIGHT:Theme.DARK;rebuild();}).tooltip(Tooltip.create(Component.literal("Switches between dark and light wallet themes."))).bounds(left+270,action,66,20).build());
            }
            if(tab==Tab.ORGANIZATIONS){int y=top+76;for(AccountAccess access:accounts.stream().filter(value->value.organizationId().isPresent()).limit(4).toList()){AccountAccess row=access;addRenderableWidget(Button.builder(Component.literal("Use "+row.organizationRole().map(Enum::name).orElse("MEMBER")+" account"),button->send(new WalletRequest(nextId(),WalletProtocol.Request.SELECT_ACCOUNT,Optional.of(row.accountId()),Optional.empty(),0,Optional.empty(),0))).bounds(left+18,y,188,20).build());y+=24;}}
        }
        private void preview(){if(submitting)return;String name=recipient.getValue().trim();long units;try{units=Long.parseLong(amount.getValue().trim());}catch(NumberFormatException ignored){status="Enter a positive whole Coin amount";return;}if(!name.matches("[A-Za-z0-9_]{1,16}")){status="Enter a valid player name";return;}if(units<=0||selected.isEmpty()){status="Select a source account and enter a positive amount";return;}submitting=true;status=WAITING_FOR_SERVER;send(WalletRequest.previewByName(nextId(),selected.get().id(),name,units));}
        private void refreshHistory(){if(selected.isEmpty())return;historyOffset=0;send(new WalletRequest(nextId(),WalletProtocol.Request.REQUEST_HISTORY,selected.map(AccountSnapshot::id),Optional.empty(),0,Optional.empty(),100));}
        private void confirm(){if(preview.isEmpty()||submitting)return;submitting=true;status=WAITING_FOR_SERVER;send(new WalletRequest(nextId(),WalletProtocol.Request.CONFIRM_TRANSFER,Optional.empty(),Optional.empty(),0,Optional.of(preview.get().nonce()),0));}
        private void copyPlayerName(){if(Minecraft.getInstance().player==null){status="No player name is available yet";return;}Minecraft.getInstance().keyboardHandler.setClipboard(Minecraft.getInstance().player.getGameProfile().getName());status="Player name copied";}
        @Override public void tick(){super.tick();if(submitting && !WAITING_FOR_SERVER.equals(status))submitting=false;boolean confirmation=preview.isPresent()&&preview.get().confirmationRequired();if(tab==Tab.PAY&&confirmation!=displayedConfirmation)rebuild();}
        @Override public boolean mouseScrolled(double mouseX,double mouseY,double horizontalAmount,double verticalAmount){if(tab!=Tab.HISTORY||verticalAmount==0)return super.mouseScrolled(mouseX,mouseY,horizontalAmount,verticalAmount);int previous=historyOffset;int maximum=Math.max(0,history.size()-visibleHistoryRows());historyOffset=Math.max(0,Math.min(maximum,historyOffset-(int)Math.signum(verticalAmount)*3));return historyOffset!=previous||super.mouseScrolled(mouseX,mouseY,horizontalAmount,verticalAmount);}
        @Override public boolean mouseClicked(double mouseX,double mouseY,int button){
            int left=left(),top=top();
            if(button==0&&mouseX>=left+walletWidth()-14&&mouseY>=top+panelHeight()-14){resizing=true;resizeAnchorLeft=left;resizeAnchorTop=top;return true;}
            if(button==0&&mouseX>=left&&mouseX<left+260&&mouseY>=top&&mouseY<top+26){dragging=true;dragPointerX=(int)mouseX-left;dragPointerY=(int)mouseY-top;return true;}
            return super.mouseClicked(mouseX,mouseY,button);
        }
        @Override public boolean mouseDragged(double mouseX,double mouseY,int button,double dragX,double dragY){
            if(resizing&&button==0){walletWidth=Math.max(348,Math.min(width-12,(int)mouseX-resizeAnchorLeft+1));walletHeight=Math.max(214,Math.min(height-12,(int)mouseY-resizeAnchorTop+1));offsetX=resizeAnchorLeft-(width-walletWidth())/2;offsetY=resizeAnchorTop-(height-panelHeight())/2;rebuild();return true;}
            if(dragging&&button==0){offsetX=(int)mouseX-dragPointerX-(width-walletWidth())/2;offsetY=(int)mouseY-dragPointerY-(height-panelHeight())/2;rebuild();return true;}
            return super.mouseDragged(mouseX,mouseY,button,dragX,dragY);
        }
        @Override public boolean mouseReleased(double mouseX,double mouseY,int button){if(button==0&&(dragging||resizing)){dragging=false;resizing=false;return true;}return super.mouseReleased(mouseX,mouseY,button);}
        @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partial){int left=left(),top=top(),bottom=top+panelHeight();drawInventoryFrame(graphics,left,top,left+walletWidth(),bottom);int titleX=left+12;if(walletIcon.isPresent()){graphics.blit(walletIcon.get(),titleX,top+6,0,0,16,16,16,16);titleX+=20;}graphics.drawString(font,trimToWidth(walletName+" / WALLET",left+walletWidth()-100-titleX),titleX,top+8,primaryText(),false);graphics.drawString(font,"VERIFIED",left+walletWidth()-86,top+8,labelText(),false);graphics.fill(left+10,top+52,left+walletWidth()-10,top+54,theme==Theme.DARK?0xFF555555:0xFF777777);int y=top+58;switch(tab){case OVERVIEW->overview(graphics,left,y);case PAY->pay(graphics,left,y);case HISTORY->history(graphics,left,y);case ORGANIZATIONS->organizations(graphics,left,y);case LEADERBOARD->leaderboard(graphics,left,y);}if(tab!=Tab.HISTORY&&!status.isBlank())graphics.drawString(font,shortStatus(),left+12,bottom-16,labelText(),false);for(Renderable renderable:renderables)renderable.render(graphics,mouseX,mouseY,partial);if(theme==Theme.DARK)drawDarkButtons(graphics);drawSelectedTab(graphics,left,top);drawResizeGrip(graphics,left+walletWidth(),bottom);}
        private int walletWidth(){return Math.min(Math.max(348,walletWidth),Math.max(1,width-12));}
        private int panelHeight(){return Math.min(Math.max(214,walletHeight),Math.max(1,height-12));}
        private int left(){return Math.max(0,Math.min(width-walletWidth(),(width-walletWidth())/2+offsetX));}
        private int top(){return Math.max(6,Math.min(height-panelHeight()-6,(height-panelHeight())/2+offsetY));}
        private boolean compact(){return panelHeight()<214;}
        private boolean ultraCompact(){return height<176;}
        private int actionY(){return top()+panelHeight()-42;}
        private int tabStart(int left){return left+(walletWidth()-336)/2;}
        private String shortStatus(){int max=336;String value=status;while(font.width(value)>max&&value.length()>3)value=value.substring(0,value.length()-4)+"...";return value;}
        private void drawInventoryFrame(GuiGraphics graphics,int left,int top,int right,int bottom){graphics.fill(left-2,top-2,right+2,bottom+2,0xFF000000);graphics.fill(left,top,right,bottom,panelColor());graphics.fill(left,top,right,top+2,theme==Theme.DARK?0xFF555555:0xFFFFFFFF);graphics.fill(left,top,left+2,bottom,theme==Theme.DARK?0xFF555555:0xFFFFFFFF);graphics.fill(left,bottom-2,right,bottom,theme==Theme.DARK?0xFF111111:0xFF555555);graphics.fill(right-2,top,right,bottom,theme==Theme.DARK?0xFF111111:0xFF555555);}
        private int panelColor(){return theme==Theme.DARK?BLACK:0xFFC6C6C6;}
        private int primaryText(){return theme==Theme.DARK?WHITE:0xFF404040;}
        private int secondaryText(){return theme==Theme.DARK?0xFFB8B8B8:0xFF666666;}
        private int labelText(){return theme==Theme.DARK?0xFFB8B8B8:0xFF404040;}
        private int sectionText(){return theme==Theme.DARK?WHITE:0xFF404040;}
        private void drawDarkButtons(GuiGraphics graphics){
            for(Renderable renderable:renderables) if(renderable instanceof Button button){
                int left=button.getX(),top=button.getY(),right=button.getRight(),bottom=button.getBottom();
                boolean hover=button.isHoveredOrFocused();
                graphics.fill(left,top,right,bottom,0xFF000000);
                graphics.fill(left+1,top+1,right-1,bottom-1,hover?0xFF4B4B4B:0xFF303030);
                graphics.fill(left+2,top+2,right-2,top+3,hover?0xFF777777:0xFF555555);
                graphics.drawCenteredString(font,button.getMessage(),left+button.getWidth()/2,top+(button.getHeight()-8)/2,button.active?WHITE:0xFF888888);
            }
        }
        private void drawSelectedTab(GuiGraphics graphics,int left,int top){
            int x=tabStart(left)+tab.ordinal()*68;
            int fill=theme==Theme.DARK?0xFF5A5A5A:0xFF777777;
            graphics.fill(x,top+28,x+64,top+48,0xFF000000);
            graphics.fill(x+1,top+1+28,x+63,top+47,fill);
            graphics.fill(x+2,top+30,x+62,top+32,theme==Theme.DARK?0xFF888888:0xFFAAAAAA);
            graphics.drawCenteredString(font,tab.label,x+32,top+34,WHITE);
        }
        private void drawResizeGrip(GuiGraphics graphics,int right,int bottom){int color=theme==Theme.DARK?0xFF888888:0xFF555555;for(int offset=3;offset<=9;offset+=3)graphics.fill(right-offset,bottom-2,right-1,bottom-offset,color);}
        private void overview(GuiGraphics g,int left,int y){
            if(selected.isPresent()){
                AccountSnapshot account=selected.get();
                g.drawString(font,"Balance",left+18,y,labelText(),false);
                g.drawString(font,coinText(account.balance().units()),left+18,y+14,primaryText(),false);
                String player=Minecraft.getInstance().player==null?"Unknown":Minecraft.getInstance().player.getGameProfile().getName();
                g.drawString(font,"Account: "+player+"  |  "+account.lifecycle().name(),left+18,y+32,secondaryText(),false);
                String activity=history.isEmpty()?"Recent activity: open History to load records.":"Recent: "+history.getFirst().direction()+" "+coinText(history.getFirst().amount());
                g.drawString(font,trimToWidth(activity,320),left+18,y+50,secondaryText(),false);
            }else g.drawString(font,"No wallet snapshot received.",left+18,y,labelText(),false);
            g.drawString(font,"Payments are verified by the server.",left+18,y+74,secondaryText(),false);
        }
        private void pay(GuiGraphics g,int left,int y){if(ultraCompact())return;g.drawString(font,"Use a verified player name.",left+18,y,secondaryText(),false);if(preview.isPresent()){var value=preview.get();int previewY=top()+(compact()?110:132);g.fill(left+14,previewY-4,left+346,previewY+28,theme==Theme.DARK?0xFF1A1A1A:0xFF8B8B8B);g.drawString(font,"Amount "+value.amount()+" Fee "+value.fee()+" Total "+value.totalDebit(),left+18,previewY,primaryText(),false);g.drawString(font,value.confirmationRequired()?"Confirm before payment.":"Server validates payment.",left+18,previewY+14,secondaryText(),false);}}
        private int visibleHistoryRows(){return Math.max(1,(panelHeight()-96)/16);}
        private void history(GuiGraphics g,int left,int y){int row=y;int rows=visibleHistoryRows();int maximum=Math.max(0,history.size()-rows);historyOffset=Math.min(historyOffset,maximum);for(TransactionRecord record:history.stream().skip(historyOffset).limit(rows).toList()){String line=record.direction()+" "+coinText(record.amount())+" "+record.description();g.drawString(font,trimToWidth(line,320),left+18,row,primaryText(),false);row+=16;}if(history.isEmpty())g.drawString(font,"No records loaded.",left+18,row,secondaryText(),false);else if(maximum>0)g.drawString(font,(historyOffset+1)+"-"+Math.min(history.size(),historyOffset+rows)+" / "+history.size(),left+18,top()+panelHeight()-16,labelText(),false);}
        private String trimToWidth(String value,int maximum){while(font.width(value)>maximum&&value.length()>3)value=value.substring(0,value.length()-4)+"...";return value;}
        private void organizations(GuiGraphics g,int left,int y){if(accounts.stream().noneMatch(value->value.organizationId().isPresent()))g.drawString(font,"No organization accounts are currently authorized.",left+18,y,secondaryText(),false);else g.drawString(font,"Your current server role controls every action.",left+18,y,secondaryText(),false);}
        private void leaderboard(GuiGraphics g,int left,int y){g.drawString(font,"No public rankings are available.",left+18,y,secondaryText(),false);g.drawString(font,"Private accounts stay private.",left+18,y+18,secondaryText(),false);}
        @Override public boolean keyPressed(int key,int scan,int modifiers){if(key==GLFW.GLFW_KEY_ESCAPE||(key==GLFW.GLFW_KEY_B&&(recipient==null||(!recipient.isFocused()&&!amount.isFocused())))){onClose();return true;}return super.keyPressed(key,scan,modifiers);}
        private enum Tab { OVERVIEW("Overview"),PAY("Pay"),HISTORY("History"),ORGANIZATIONS("Orgs"),LEADERBOARD("Ranks");final String label;Tab(String label){this.label=label;} }
    }
    private static final class HudSettingsScreen extends Screen {
        private final Screen parent;
        HudSettingsScreen(Screen parent){super(Component.literal("HUD settings"));this.parent=parent;}
        @Override protected void init(){
            int left=(width-220)/2,top=(height-190)/2;
            addRenderableWidget(Button.builder(Component.literal(hudEnabled?"HUD: on":"HUD: off"),button->{hudEnabled=!hudEnabled;rebuildSettings();}).bounds(left+14,top+34,192,20).build());
            addRenderableWidget(Button.builder(Component.literal("Position: "+hudAnchor.label),button->{hudAnchor=hudAnchor.next();rebuildSettings();}).bounds(left+14,top+58,192,20).build());
            addRenderableWidget(Button.builder(Component.literal("Scale: "+scaleLabel()),button->{hudScale=nextScale();rebuildSettings();}).bounds(left+14,top+82,92,20).build());
            addRenderableWidget(Button.builder(Component.literal("Opacity: "+hudOpacityPercent+"%"),button->{hudOpacityPercent=hudOpacityPercent==0?100:hudOpacityPercent-10;rebuildSettings();}).bounds(left+114,top+82,92,20).build());
            addRenderableWidget(Button.builder(Component.literal("Name: "+hudNameColor.label),button->{hudNameColor=hudNameColor.next();rebuildSettings();}).bounds(left+14,top+106,192,20).build());
            addRenderableWidget(Button.builder(Component.literal("Coins: "+hudBalanceColor.label),button->{hudBalanceColor=hudBalanceColor.next();rebuildSettings();}).bounds(left+14,top+130,192,20).build());
            addRenderableWidget(Button.builder(Component.literal("Done"),button->onClose()).bounds(left+62,top+160,96,20).build());
        }
        private String scaleLabel(){return hudScale==0.5F?"0.5x":hudScale==1.5F?"1.5x":"1x";}
        private float nextScale(){return hudScale==0.5F?1.0F:hudScale==1.0F?1.5F:0.5F;}
        private void rebuildSettings(){clearWidgets();init();}
        @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partial){int left=(width-220)/2,top=(height-190)/2;graphics.fill(left-2,top-2,left+222,top+192,0xFF000000);graphics.fill(left,top,left+220,top+190,theme==Theme.DARK?0xFF121212:0xFFC6C6C6);graphics.drawString(font,"HUD settings",left+12,top+12,theme==Theme.DARK?0xFFF8FAFC:0xFF404040,false);graphics.drawString(font,"Local display preferences",left+12,top+24,theme==Theme.DARK?0xFFB8B8B8:0xFF666666,false);super.render(graphics,mouseX,mouseY,partial);}
        @Override public void onClose(){Minecraft.getInstance().setScreen(parent);}
    }
    private enum Theme { DARK, LIGHT }
    private enum HudAnchor {
        TOP_LEFT("Top left"),TOP_RIGHT("Top right"),BOTTOM_RIGHT("Bottom right"),BOTTOM_LEFT("Bottom left");
        private final String label;
        HudAnchor(String label){this.label=label;}
        private HudAnchor next(){return values()[(ordinal()+1)%values().length];}
    }
    private enum HudTextColor {
        THEME("Theme",0), WHITE("White",0xFFF8FAFC), GOLD("Gold",0xFFFFCC33), AQUA("Aqua",0xFF55FFFF), GREEN("Green",0xFF55FF55), RED("Red",0xFFFF5555);
        private final String label;
        private final int color;
        HudTextColor(String label,int color){this.label=label;this.color=color;}
        private HudTextColor next(){return values()[(ordinal()+1)%values().length];}
        private int resolve(int themeColor){return this==THEME?themeColor:color;}
    }
}
