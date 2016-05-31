package net.minecraftforge.common.plon;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.regex.Pattern;

/**
 * Created by rainwarrior on 5/19/16.
 */
public enum AST
{
    ;

    // public
    public static interface ISExp
    {
        ISExp getType();
    }

    public static ISExp makeSymbol(String name)
    {
        return new Symbol(name);
    }

    public static ISExp makeString(String name)
    {
        return new StringAtom(name);
    }

    public static ISExp makeFloat(float value)
    {
        return new FloatAtom(value);
    }

    // private
    private static final Pattern opsPattern = Pattern.compile("[+\\-*/mMrRfF]+");

    static interface IAtom extends ISExp {}

    static interface IStringAtom extends IAtom
    {
        String value();
    }

    static interface ICallableExp extends ISExp
    {
        ISExp apply(IList args);
    }

    static enum Unbound implements ISExp
    {
        INSTANCE;

        @Override
        public String toString()
        {
            return "&unbound";
        }

        @Override
        public ISExp getType()
        {
            return PrimTypes.Invalid.type;
        }
    }

    static final class FloatAtom implements IAtom
    {
        final float value;

        private FloatAtom(float value)
        {
            this.value = value;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FloatAtom floatAtom = (FloatAtom) o;
            return Float.compare(floatAtom.value, value) == 0;
        }

        @Override
        public int hashCode()
        {
            return Objects.hashCode(value);
        }

        @Override
        public String toString()
        {
            return Float.toString(value);
        }

        @Override
        public ISExp getType()
        {
            return PrimTypes.Float.type;
        }
    }

    static final class StringAtom implements IStringAtom
    {
        final String value;

        private StringAtom(String value)
        {
            this.value = value;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            StringAtom that = (StringAtom) o;
            return Objects.equal(value, that.value);
        }

        @Override
        public int hashCode()
        {
            return Objects.hashCode(value);
        }

        @Override
        public String toString()
        {
            return "\" " + value + "\"";
        }

        @Override
        public String value()
        {
            return " " + value;
        }

        @Override
        public ISExp getType()
        {
            return PrimTypes.String.type;
        }
    }

    static final class Symbol implements IStringAtom
    {
        final String value;

        private Symbol(String value)
        {
            this.value = value.intern();
        }

        @SuppressWarnings("StringEquality")
        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Symbol that = (Symbol) o;
            return value == that.value;
        }

        @Override
        public int hashCode()
        {
            return Objects.hashCode(value);
        }

        @Override
        public String toString()
        {
            return "\"#" + value + "\"";
        }

        @Override
        public String value()
        {
            return "#" + value;
        }

        @Override
        public ISExp getType()
        {
            return PrimTypes.Symbol.type;
        }
    }

    static class ArithmSymbol implements IStringAtom
    {
        private final String ops;

        private ArithmSymbol(String ops)
        {
            this.ops = ops;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ArithmSymbol that = (ArithmSymbol) o;
            return Objects.equal(ops, that.ops);
        }

        @Override
        public int hashCode()
        {
            return Objects.hashCode(ops);
        }

        @Override
        public String toString()
        {
            return "\"" + ops + "\"";
        }

        @Override
        public String value()
        {
            return ops;
        }

        @Override
        public ISExp getType()
        {
            return PrimTypes.Symbol.type;
        }
    }

    static class ArithmOp implements ICallableExp
    {
        static ISExp makeArithmType(ArithmSymbol ops)
        {
            return absType(ImmutableList.copyOf(Collections.nCopies(ops.ops.length() + 1, PrimTypes.Float.type)), PrimTypes.Float.type);
        }

        private final String ops;
        private final ISExp type;

        ArithmOp(ArithmSymbol ops)
        {
            this.ops = ops.ops;
            this.type = makeArithmType(ops);
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ArithmOp that = (ArithmOp) o;
            return Objects.equal(ops, that.ops);
        }

        @Override
        public int hashCode()
        {
            return Objects.hashCode(ops);
        }

        @Override
        public String toString()
        {
            return "\"" + ops + "\"";
        }

        @Override
        public FloatAtom apply(IList args)
        {
            if (length(args) != ops.length() + 1)
            {
                throw new IllegalArgumentException("arithmetic operator string \"" + ops + "\" needs " + (ops.length() + 1) + " arguments, got " + args);
            }
            Cons cons = (Cons) args;
            if (!(cons.car instanceof FloatAtom))
            {
                throw new IllegalArgumentException("arithmetic operator needs a number, got " + cons.car);
            }
            float ret = ((FloatAtom) cons.car).value;
            for (int i = 0; i < ops.length(); i++)
            {
                cons = (Cons) cons.cdr;
                if (!(cons.car instanceof FloatAtom))
                {
                    throw new IllegalArgumentException("arithmetic operator needs a number, got " + cons.car);
                }
                float arg = ((FloatAtom) cons.car).value;
                switch (ops.charAt(i))
                {
                    case '+': ret += arg; break;
                    case '-': ret -= arg; break;
                    case '*': ret *= arg; break;
                    case '/': ret /= arg; break;
                    case 'm': ret = Math.min(ret, arg); break;
                    case 'M': ret = Math.max(ret, arg); break;
                    case 'r': ret = (float) Math.floor(ret / arg) * arg; break;
                    case 'R': ret = (float) Math.ceil(ret / arg) * arg; break;
                    case 'f': ret -= Math.floor(ret / arg) * arg; break;
                    case 'F': ret = (float) Math.ceil(ret / arg) * arg - ret; break;
                    default: throw new IllegalArgumentException("Unknown operator:" + ops.charAt(i));
                }
            }
            return new FloatAtom(ret);
        }

        @Override
        public ISExp getType()
        {
            return type;
        }
    }

    static Map loadFrame(Map map)
    {
        ImmutableMap.Builder<ISExp, ISExp> frameBuilder = ImmutableMap.builder();
        for (java.util.Map.Entry<? extends ISExp, ? extends ISExp> entry : map.value.entrySet())
        {
            Interpreter.addLabel(frameBuilder, entry.getKey(), entry.getValue());
        }
        /*ImmutableMap<ISExp, ISExp> frame = frameBuilder.build();
        env = new Cons(new Map(frame), env);
        for (java.util.Map.Entry<ISExp, ISExp> entry : frame.entrySet())
        {
            frameBuilder.put(entry.getKey(), eval(entry.getValue(), env));
        }*/
        return new Map(frameBuilder.build());
    }

    static abstract class Load implements ICallableExp
    {
        @Override
        public ISExp getType()
        {
            return PrimTypes.Invalid.type;
        }

        @Override
        public ISExp apply(IList args)
        {
            if (length(args) != 1)
            {
                throw new IllegalArgumentException("&load needs 1 argument, got " + args);
            }
            Cons cons = (Cons) args;
            if (cons.car instanceof StringAtom)
            {
                String location = ((StringAtom) cons.car).value;
                return read(location);
            }
            throw new IllegalArgumentException("&load needs string argument, got " + cons.car);
        }

        protected abstract ISExp read(String location);

        @Override
        public String toString()
        {
            return "&load";
        }
    }

    enum PrimOp implements ICallableExp
    {
        Length("length", ImmutableList.of(PrimTypes.List.type), PrimTypes.Float.type)
        {
            @Override
            public FloatAtom apply(IList args)
            {
                if (args == Nil.INSTANCE)
                {
                    throw new IllegalArgumentException("Length with no arguments");
                }
                Cons cons = (Cons) args;
                if (cons.cdr != Nil.INSTANCE)
                {
                    throw new IllegalArgumentException("Length has too many arguments: " + cons);
                }
                return new FloatAtom(length(cons.car));
            }
        },
        Mlength("mlength", ImmutableList.of(PrimTypes.Map.type), PrimTypes.Float.type)
        {
            @Override
            public FloatAtom apply(IList args)
            {
                if (args == Nil.INSTANCE)
                {
                    throw new IllegalArgumentException("Length with no arguments");
                }
                Cons cons = (Cons) args;
                if (cons.cdr != Nil.INSTANCE)
                {
                    throw new IllegalArgumentException("Length has too many arguments: " + cons);
                }
                return new FloatAtom(length(cons.car));
            }
        },
        Cons("cons", ImmutableList.<ISExp>of(new Symbol("T"),  PrimTypes.List.type), PrimTypes.List.type)
        {
            @Override
            public ISExp apply(IList args)
            {
                if (length(args) != 2)
                {
                    throw new IllegalArgumentException("Cons needs 2 arguments, got: " + args);
                }
                Cons c1 = (Cons) args;
                Cons c2 = (Cons) c1.cdr;
                if (!(c2.car instanceof IList))
                {
                    throw new IllegalArgumentException("Cons needs a list as a second argument");
                }
                return new Cons(c1.car, (IList) c2.car);
            }
        },
        Car("car")
        {
            @Override
            public ISExp apply(IList args)
            {
                if (length(args) != 1)
                {
                    throw new IllegalArgumentException("Car needs 1 argument, got: " + args);
                }
                Cons cons = (Cons) args;
                if (cons.car instanceof Cons)
                {
                    return ((Cons) cons.car).car;
                }
                throw new IllegalArgumentException("Car called not on a list");
            }
        },
        Cdr("cdr", ImmutableList.<ISExp>of(PrimTypes.List.type), PrimTypes.List.type)
        {
            @Override
            public ISExp apply(IList args)
            {
                if (length(args) != 1)
                {
                    throw new IllegalArgumentException("Cdr needs 1 argument, got: " + args);
                }
                Cons cons = (Cons) args;
                if (cons.car instanceof Cons)
                {
                    return ((Cons) cons.car).cdr;
                }
                throw new IllegalArgumentException("Cdr called not on a list");
            }
        },
        List("list")
        {
            @Override
            public ISExp apply(IList args)
            {
                return args;
            }
        },
        Map("map")
        {
            @Override
            public ISExp apply(IList args)
            {
                return new Map(args);
            }
        },
        Conm("conm", ImmutableList.<ISExp>of(new Symbol("K"), new Symbol("V"), PrimTypes.Map.type), PrimTypes.Map.type)
        {
            @Override
            public ISExp apply(IList args)
            {
                if (length(args) != 3)
                {
                    throw new IllegalArgumentException("Conm needs 3 arguments, got: " + args);
                }
                Cons c1 = (Cons) args;
                Cons c2 = (Cons) c1.cdr;
                Cons c3 = (Cons) c2.cdr;
                if (!(c3.car instanceof IMap))
                {
                    throw new IllegalArgumentException("Conm needs a map as a third argument");
                }
                IMap map = (IMap) c3.car;
                if(map == MNil.INSTANCE)
                {
                    return new Map(ImmutableMap.of(c1.car, c2.car));
                }
                Map oldMap = (AST.Map) map;
                java.util.Map<ISExp, ISExp> newMap = Maps.newHashMap();
                newMap.putAll(oldMap.value);
                newMap.put(c1.car, c2.car);
                return new Map(ImmutableMap.copyOf(newMap));
            }
        },
        Carm("carm", ImmutableList.<ISExp>of(PrimTypes.Map.type), PrimTypes.List.type)
        {
            @Override
            public ISExp apply(IList args)
            {
                if (length(args) != 1)
                {
                    throw new IllegalArgumentException("Carm needs 1 argument, got: " + args);
                }
                Cons c1 = (Cons) args;
                if (!(c1.car instanceof Map))
                {
                    throw new IllegalArgumentException("Carm needs a non-empty map as an argument");
                }
                // immutable map iterator is stable, so this is consistent with cdrm.
                java.util.Map.Entry<? extends ISExp, ? extends ISExp> entry = ((Map) c1.car).value.entrySet().iterator().next();
                return new Cons(entry.getKey(), new Cons(entry.getValue(), Nil.INSTANCE));
            }
        },
        Cdrm("cdrm", ImmutableList.<ISExp>of(PrimTypes.Map.type), PrimTypes.Map.type)
        {
            @Override
            public ISExp apply(IList args)
            {
                if (length(args) != 1)
                {
                    throw new IllegalArgumentException("Cdrm needs 1 argument, got: " + args);
                }
                Cons c1 = (Cons) args;
                if (!(c1.car instanceof Map))
                {
                    throw new IllegalArgumentException("Cdrm needs a non-empty map as an argument");
                }
                // immutable map iterator is stable, so this is consistent with carm.
                Iterator<? extends java.util.Map.Entry<? extends ISExp, ? extends ISExp>> iterator = ((Map) c1.car).value.entrySet().iterator();
                iterator.next();
                ImmutableMap.Builder<ISExp, ISExp> builder = ImmutableMap.builder();
                while(iterator.hasNext())
                {
                    java.util.Map.Entry<? extends ISExp, ? extends ISExp> entry = iterator.next();
                    builder.put(entry);
                }
                ImmutableMap<ISExp, ISExp> map = builder.build();
                if(map.isEmpty())
                {
                    return MNil.INSTANCE;
                }
                return new Map(map);
            }
        },
        // TODO: should be a library function eventually
        Keys("keys", ImmutableList.<ISExp>of(PrimTypes.Map.type), PrimTypes.List.type)
        {
            @Override
            public ISExp apply(IList args)
            {
                if (length(args) != 1)
                {
                    throw new IllegalArgumentException("keys needs 1 argument, got: " + args);
                }
                Cons c1 = (Cons) args;
                if (!(c1.car instanceof Map))
                {
                    throw new IllegalArgumentException("keys needs a non-empty map as an argument");
                }
                Map map = (Map) c1.car;
                IList keys = Nil.INSTANCE;
                for(ISExp key : map.value.keySet())
                {
                    keys = new Cons(key, keys);
                }
                return keys;
            }
        };

        static final ImmutableMap<Symbol, PrimOp> values;

        static
        {
            ImmutableMap.Builder<Symbol, PrimOp> builder = ImmutableMap.builder();
            for (PrimOp op : values())
            {
                builder.put(op.name, op);
            }
            values = builder.build();
        }

        private final Symbol name;
        private final ISExp type;

        private PrimOp(String name)
        {
            this.name = new Symbol(name);
            this.type = PrimTypes.Invalid.type;
        }

        private PrimOp(String name, ImmutableList<? extends ISExp> args, ISExp ret)
        {
            this.name = new Symbol(name);
            this.type = absType(args, ret);
        }

        @Override
        public ISExp getType()
        {
            return type;
        }

        @Override
        public String toString()
        {
            return "&" + name.value;
        }
    }

    static interface IList extends ISExp {}

    static enum Nil implements IList, IAtom
    {
        INSTANCE;

        @Override
        public String toString()
        {
            return "[]";
        }

        @Override
        public ISExp getType()
        {
            return PrimTypes.List.type;
        }
    }

    static final class Cons implements IList
    {
        final ISExp car;
        final IList cdr;

        Cons(ISExp car, IList cdr)
        {
            this.car = car;
            this.cdr = cdr;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Cons cons = (Cons) o;
            return Objects.equal(car, cons.car) && Objects.equal(cdr, cons.cdr);
        }

        @Override
        public int hashCode()
        {
            return Objects.hashCode(car, cdr);
        }

        private String cdrString()
        {
            if (cdr == Nil.INSTANCE)
            {
                return "";
            }
            Cons cdr = (Cons) this.cdr;
            return ", " + cdr.car.toString() + cdr.cdrString();
        }

        @Override
        public String toString()
        {
            return "[" + car + cdrString() + "]";
        }

        @Override
        public ISExp getType()
        {
            return PrimTypes.List.type;
        }
    }

    static interface IMap extends ISExp {}

    static enum MNil implements IMap, IAtom
    {
        INSTANCE;

        @Override
        public String toString()
        {
            return "{}";
        }

        @Override
        public ISExp getType()
        {
            return PrimTypes.Map.type;
        }
    }

    static final class Map implements IMap
    {
        final ImmutableMap<? extends ISExp, ? extends ISExp> value;
        private final transient boolean isJsonifiable;

        public Map(IList list)
        {
            this(buildMap(list));
        }

        private static ImmutableMap<ISExp, ISExp> buildMap(IList list)
        {
            ImmutableMap.Builder<ISExp, ISExp> builder = ImmutableMap.builder();
            while (list != Nil.INSTANCE)
            {
                Cons c1 = (Cons) list;
                Cons c2 = (Cons) c1.cdr;
                builder.put(c1.car, c2.car);
                list = c2.cdr;
            }
            return builder.build();
        }

        public Map(ImmutableMap<? extends ISExp, ? extends ISExp> value)
        {
            this.value = value;
            boolean isJsonifiable = true;
            for (ISExp exp : value.keySet())
            {
                if (!(exp instanceof IStringAtom))
                {
                    isJsonifiable = false;
                    break;
                }
            }
            this.isJsonifiable = isJsonifiable;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Map map = (Map) o;
            return Objects.equal(value, map.value);
        }

        @Override
        public int hashCode()
        {
            return Objects.hashCode(value);
        }

        @Override
        public String toString()
        {
            if (isJsonifiable)
            {
                return "{ " + Joiner.on(", ").withKeyValueSeparator(": ").join(value) + " }";
            }
            return "[\"#&map\", " + Joiner.on(", ").withKeyValueSeparator(", ").join(value) + "]";
        }

        @Override
        public ISExp getType()
        {
            return PrimTypes.Map.type;
        }
    }

    static enum PrimTypes
    {
        Float("float"),
        String("string"),
        Symbol("symbol"),
        List("list"),
        Map("map"),
        Invalid("invalid");

        final StringAtom type;

        PrimTypes(String type)
        {
            this.type = new StringAtom(type);
        }
    }

    static ISExp absType(ImmutableList<? extends ISExp> args, ISExp ret)
    {
        Cons type = new Cons(ret, Nil.INSTANCE);
        for (ISExp arg : args.reverse())
        {
            type = new Cons(arg, type);
        }
        return type;
    }

    public static enum SExpTypeAdapterFactory implements TypeAdapterFactory
    {
        INSTANCE;

        @SuppressWarnings("unchecked")
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type)
        {
            if (type.getRawType() != ISExp.class)
            {
                return null;
            }

            return (TypeAdapter<T>) new TypeAdapter<ISExp>()
            {
                public void write(JsonWriter out, ISExp parameter) throws IOException
                {
                    if (parameter instanceof FloatAtom)
                    {
                        out.value(((FloatAtom) parameter).value);
                    }
                    else if (parameter instanceof Symbol)
                    {
                        out.value("#" + ((Symbol) parameter).value);
                    }
                    else if (parameter == Nil.INSTANCE)
                    {
                        out.beginArray();
                        out.endArray();
                    }
                    else if (parameter instanceof Cons)
                    {
                        out.beginArray();
                        write(out, ((Cons) parameter).car);
                        for (IList cdr = ((Cons) parameter).cdr; cdr instanceof Cons; cdr = ((Cons) cdr).cdr)
                        {
                            write(out, ((Cons) cdr).car);
                        }
                        out.endArray();
                    }
                    else if (parameter == MNil.INSTANCE)
                    {
                        out.beginObject();
                        out.endObject();
                    }
                    else if (parameter instanceof Map)
                    {
                        Map map = (Map) parameter;
                        if(map.isJsonifiable)
                        {
                            out.beginObject();
                            for(java.util.Map.Entry<? extends ISExp, ? extends ISExp> entry : map.value.entrySet())
                            {
                                out.name(((IStringAtom)entry.getKey()).value());
                                write(out, entry.getValue());
                            }
                            out.endObject();
                        }
                        else
                        {
                            out.beginArray();
                            write(out, Interpreter.mapSymbol);
                            for(java.util.Map.Entry<? extends ISExp, ? extends ISExp> entry : map.value.entrySet())
                            {
                                write(out, entry.getKey());
                                write(out, entry.getValue());
                            }
                            out.endArray();
                        }
                    }
                }

                private IList readCdr(JsonReader in) throws IOException
                {
                    if (!in.hasNext())
                    {
                        return Nil.INSTANCE;
                    }
                    return new Cons(read(in), readCdr(in));
                }

                private ISExp parseString(String string)
                {
                    if (string.startsWith("#"))
                    {
                        return new Symbol(string.substring(1));
                    }
                    if (string.startsWith(" "))
                    {
                        return new StringAtom(string.substring(1));
                    }
                    if (opsPattern.matcher(string).matches())
                    {
                        return new ArithmSymbol(string);
                    }
                    throw new JsonParseException("Unknown string: \"" + string + "\"");
                }

                public ISExp read(JsonReader in) throws IOException
                {
                    switch (in.peek())
                    {
                        case STRING:
                            return parseString(in.nextString());
                        case NAME:
                            return parseString(in.nextName());
                        case NUMBER:
                            return new FloatAtom((float) in.nextDouble());
                        case BEGIN_ARRAY:
                            in.beginArray();
                            IList list = readCdr(in);
                            in.endArray();
                            if (list instanceof Cons)
                            {
                                Cons cons = (Cons) list;
                                if (cons.car.equals(Interpreter.mapSymbol))
                                {
                                    // de-sugaring the map with non-string keys
                                    Map map = new Map(cons.cdr);
                                    if (map.value.isEmpty())
                                    {
                                        return MNil.INSTANCE;
                                    }
                                    return map;
                                }
                            }
                            return list;
                        case BEGIN_OBJECT:
                            in.beginObject();
                            ImmutableMap.Builder<ISExp, ISExp> builder = ImmutableMap.builder();
                            while (in.hasNext())
                            {
                                ISExp key = read(in);
                                ISExp value = read(in);
                                builder.put(key, value);
                            }
                            in.endObject();
                            return new Map(builder.build());
                        default:
                            throw new JsonParseException("Unexpected item in the bagging area: \"" + in.peek() + "\"");
                    }
                }
            };
        }
    }

    static int length(ISExp exp)
    {
        if (exp == Nil.INSTANCE || exp == MNil.INSTANCE)
        {
            return 0;
        }
        if (exp instanceof Cons)
        {
            return 1 + length(((Cons) exp).cdr);
        }
        if (exp instanceof Map)
        {
            return ((Map) exp).value.size();
        }
        throw new IllegalArgumentException("Length called neither on a list nor on a map");
    }
}
