package com.gogleox.scrip.network;

import com.gogleox.scrip.account.*;
import com.gogleox.scrip.payment.PaymentPreview;
import com.gogleox.scrip.organization.AccountAccess;
import com.gogleox.scrip.organization.OrganizationId;
import com.gogleox.scrip.organization.OrganizationRole;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/** Strict binary codec shared by loader adapters. It is bounded before domain dispatch. */
public final class WalletWireCodec {
    private static final int MAX_TEXT_BYTES = 256;
    private WalletWireCodec() { }

    public static byte[] encodeRequest(WalletRequest request) {
        try { var out = new ByteArrayOutputStream(); var data = new DataOutputStream(out);
            data.writeLong(request.requestId()); data.writeByte(request.type().ordinal()); writeOptionalAccount(data, request.account()); writeOptionalAccount(data, request.recipient()); writeOptionalText(data,request.recipientName());
            data.writeLong(request.amount()); writeOptionalUuid(data, request.confirmationNonce()); data.writeInt(request.historyLimit()); return checked(out.toByteArray());
        } catch (IOException impossible) { throw new UncheckedIOException(impossible); }
    }
    public static WalletRequest decodeRequest(byte[] bytes) throws IOException {
        try { var in = input(bytes); long id=in.readLong(); var type=enumValue(WalletProtocol.Request.values(), in.readUnsignedByte()); var account=readOptionalAccount(in); var recipient=readOptionalAccount(in); var recipientName=readOptionalText(in);
            long amount=in.readLong(); var nonce=readOptionalUuid(in); int limit=in.readInt(); requireEnd(in); return new WalletRequest(id,type,account,recipient,recipientName,amount,nonce,limit);
        } catch (IllegalArgumentException e) { throw new IOException("malformed wallet request", e); }
    }
    public static byte[] encodeResponse(WalletResponse response) {
        try { var out = new ByteArrayOutputStream(); var data = new DataOutputStream(out);
            data.writeLong(response.requestId()); data.writeByte(response.type().ordinal()); writeOptionalSnapshot(data,response.account()); writeOptionalPreview(data,response.preview());
            if(response.history().size()>100) throw new IllegalArgumentException("history too large"); data.writeByte(response.history().size()); for(var record:response.history()) writeRecord(data,record);
            if(response.accessibleAccounts().size()>100) throw new IllegalArgumentException("account list too large"); data.writeByte(response.accessibleAccounts().size()); for(var access:response.accessibleAccounts()) writeAccess(data,access);
            data.writeBoolean(response.error().isPresent()); if(response.error().isPresent()) data.writeByte(response.error().get().ordinal()); writeOptionalText(data,response.detail()); return checked(out.toByteArray());
        } catch (IOException impossible) { throw new UncheckedIOException(impossible); }
    }
    public static WalletResponse decodeResponse(byte[] bytes) throws IOException {
        try { var in=input(bytes); long id=in.readLong(); var type=enumValue(WalletProtocol.Response.values(),in.readUnsignedByte()); var account=readOptionalSnapshot(in); var preview=readOptionalPreview(in);
            int count=in.readUnsignedByte(); var history=new ArrayList<TransactionRecord>(count); for(int i=0;i<count;i++) history.add(readRecord(in)); int accessCount=in.readUnsignedByte(); var access=new ArrayList<AccountAccess>(accessCount); for(int i=0;i<accessCount;i++) access.add(readAccess(in)); Optional<WalletProtocol.Error> error=in.readBoolean()?Optional.of(enumValue(WalletProtocol.Error.values(),in.readUnsignedByte())):Optional.empty(); var detail=readOptionalText(in); requireEnd(in); return new WalletResponse(id,type,account,preview,history,access,error,detail);
        } catch (IllegalArgumentException e) { throw new IOException("malformed wallet response",e); }
    }
    private static DataInputStream input(byte[] bytes) throws IOException { if(bytes==null||bytes.length>WalletProtocol.MAX_PACKET_BYTES) throw new IOException("packet too large"); return new DataInputStream(new ByteArrayInputStream(bytes)); }
    private static byte[] checked(byte[] bytes) { if(bytes.length>WalletProtocol.MAX_PACKET_BYTES) throw new IllegalArgumentException("packet too large"); return bytes; }
    private static <E extends Enum<E>> E enumValue(E[] values,int ordinal) { if(ordinal<0||ordinal>=values.length) throw new IllegalArgumentException("invalid enum"); return values[ordinal]; }
    private static void writeUuid(DataOutput out,UUID value)throws IOException {out.writeLong(value.getMostSignificantBits());out.writeLong(value.getLeastSignificantBits());}
    private static UUID readUuid(DataInput in)throws IOException{return new UUID(in.readLong(),in.readLong());}
    private static void writeOptionalUuid(DataOutput out,Optional<UUID> value)throws IOException{out.writeBoolean(value.isPresent());if(value.isPresent())writeUuid(out,value.get());}
    private static Optional<UUID> readOptionalUuid(DataInput in)throws IOException{return in.readBoolean()?Optional.of(readUuid(in)):Optional.empty();}
    private static void writeOptionalAccount(DataOutput out,Optional<AccountId> value)throws IOException{out.writeBoolean(value.isPresent());if(value.isPresent())writeUuid(out,value.get().value());}
    private static Optional<AccountId> readOptionalAccount(DataInput in)throws IOException{return in.readBoolean()?Optional.of(new AccountId(readUuid(in))):Optional.empty();}
    private static void writeSnapshot(DataOutput out,AccountSnapshot value)throws IOException{writeUuid(out,value.id().value());writeOptionalUuid(out,Optional.ofNullable(value.ownerId()));out.writeByte(value.type().ordinal());out.writeLong(value.balance().units());out.writeLong(value.createdAt().toEpochMilli());out.writeLong(value.lastLoginAt().toEpochMilli());out.writeLong(value.lastEconomicActivityAt().toEpochMilli());out.writeByte(value.lifecycle().ordinal());}
    private static AccountSnapshot readSnapshot(DataInput in)throws IOException{return new AccountSnapshot(new AccountId(readUuid(in)),readOptionalUuid(in).orElse(null),enumValue(AccountType.values(),in.readUnsignedByte()),new Balance(in.readLong()),Instant.ofEpochMilli(in.readLong()),Instant.ofEpochMilli(in.readLong()),Instant.ofEpochMilli(in.readLong()),enumValue(AccountLifecycle.values(),in.readUnsignedByte()));}
    private static void writeOptionalSnapshot(DataOutput out,Optional<AccountSnapshot> value)throws IOException{out.writeBoolean(value.isPresent());if(value.isPresent())writeSnapshot(out,value.get());}
    private static Optional<AccountSnapshot> readOptionalSnapshot(DataInput in)throws IOException{return in.readBoolean()?Optional.of(readSnapshot(in)):Optional.empty();}
    private static void writePreview(DataOutput out,PaymentPreview value)throws IOException{writeUuid(out,value.nonce());writeUuid(out,value.actorId());writeUuid(out,value.source().value());writeUuid(out,value.destination().value());out.writeLong(value.amount());out.writeLong(value.fee());out.writeLong(value.totalDebit());out.writeLong(value.policyRevision());out.writeLong(value.expiresAt().toEpochMilli());out.writeBoolean(value.confirmationRequired());}
    private static PaymentPreview readPreview(DataInput in)throws IOException{return new PaymentPreview(readUuid(in),readUuid(in),new AccountId(readUuid(in)),new AccountId(readUuid(in)),in.readLong(),in.readLong(),in.readLong(),in.readLong(),Instant.ofEpochMilli(in.readLong()),in.readBoolean());}
    private static void writeOptionalPreview(DataOutput out,Optional<PaymentPreview> value)throws IOException{out.writeBoolean(value.isPresent());if(value.isPresent())writePreview(out,value.get());}
    private static Optional<PaymentPreview> readOptionalPreview(DataInput in)throws IOException{return in.readBoolean()?Optional.of(readPreview(in)):Optional.empty();}
    private static void writeRecord(DataOutput out,TransactionRecord value)throws IOException{writeUuid(out,value.operationId());out.writeLong(value.occurredAt().toEpochMilli());out.writeByte(value.direction().ordinal());writeOptionalAccount(out,value.counterparty());out.writeLong(value.amount());writeText(out,value.description());}
    private static TransactionRecord readRecord(DataInput in)throws IOException{return new TransactionRecord(readUuid(in),Instant.ofEpochMilli(in.readLong()),enumValue(TransactionDirection.values(),in.readUnsignedByte()),readOptionalAccount(in),in.readLong(),readText(in));}
    private static void writeAccess(DataOutput out,AccountAccess value)throws IOException { writeUuid(out,value.accountId().value()); out.writeByte(value.type().ordinal()); out.writeBoolean(value.organizationId().isPresent()); if(value.organizationId().isPresent())writeUuid(out,value.organizationId().get().value()); out.writeBoolean(value.organizationRole().isPresent()); if(value.organizationRole().isPresent())out.writeByte(value.organizationRole().get().ordinal()); }
    private static AccountAccess readAccess(DataInput in)throws IOException { AccountId account=new AccountId(readUuid(in)); AccountType type=enumValue(AccountType.values(),in.readUnsignedByte()); Optional<OrganizationId> organization=in.readBoolean()?Optional.of(new OrganizationId(readUuid(in))):Optional.empty(); Optional<OrganizationRole> role=in.readBoolean()?Optional.of(enumValue(OrganizationRole.values(),in.readUnsignedByte())):Optional.empty(); return new AccountAccess(account,type,organization,role); }
    private static void writeOptionalText(DataOutput out,Optional<String> value)throws IOException{out.writeBoolean(value.isPresent());if(value.isPresent())writeText(out,value.get());}
    private static Optional<String> readOptionalText(DataInput in)throws IOException{return in.readBoolean()?Optional.of(readText(in)):Optional.empty();}
    private static void writeText(DataOutput out,String value)throws IOException{byte[] bytes=value.getBytes(StandardCharsets.UTF_8);if(bytes.length>MAX_TEXT_BYTES)throw new IllegalArgumentException("text too long");out.writeShort(bytes.length);out.write(bytes);}
    private static String readText(DataInput in)throws IOException{int length=in.readUnsignedShort();if(length>MAX_TEXT_BYTES)throw new IOException("text too long");byte[] bytes=new byte[length];in.readFully(bytes);return new String(bytes,StandardCharsets.UTF_8);}
    private static void requireEnd(DataInputStream in)throws IOException{if(in.available()!=0)throw new IOException("trailing data");}
}
