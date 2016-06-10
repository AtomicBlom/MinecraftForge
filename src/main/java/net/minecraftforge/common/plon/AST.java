package net.minecraftforge.common.plon;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
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
        ISExp getType(Unifier unifier);
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

    static IList lift(ISExp... list)
    {
        IList ret = Nil.INSTANCE;
        for (int i = list.length - 1; i >= 0; i--)
        {
            ret = new Cons(list[i], ret);
        }
        return ret;
    }

    static IList lift(ImmutableList<? extends ISExp> list)
    {
        IList ret = Nil.INSTANCE;
        for (ISExp exp : list.reverse())
        {
            ret = new Cons(exp, ret);
        }
        return ret;
    }

    static ImmutableList<ISExp> unlift(IList list)
    {
        if(list == Nil.INSTANCE)
        {
            return ImmutableList.of();
        }
        ImmutableList.Builder<ISExp> builder = ImmutableList.builder();
        while(list != Nil.INSTANCE)
        {
            Cons cons = (Cons) list;
            builder.add(cons.car);
            list = cons.cdr;
        }
        return builder.build();
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

    static interface IMacroExp extends ISExp
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
        public ISExp getType(Unifier unifier)
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
        public ISExp getType(Unifier unifier)
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
        public ISExp getType(Unifier unifier)
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
        public ISExp getType(Unifier unifier)
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
        public ISExp getType(Unifier unifier)
        {
            return PrimTypes.Symbol.type;
        }
    }

    static class ArithmOp implements ICallableExp
    {
        static ISExp makeArithmType(ArithmSymbol ops)
        {
            IList args = Nil.INSTANCE;
            for(int i = 0; i < ops.ops.length() + 1; i++)
            {
                args = new Cons(PrimTypes.Float.type, args);
            }
            return absType(args, PrimTypes.Float.type);
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
        public ISExp getType(Unifier unifier)
        {
            return type;
        }
    }

    static abstract class Load implements ICallableExp
    {
        @Override
        public ISExp getType(Unifier unifier)
        {
            // unsound
            return lift(PrimTypes.String.type, unifier.getFreshVar());
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

    enum PrimMacro implements IMacroExp
    {
        List("list")
        {
            @Override
            public ISExp apply(IList args)
            {
                if(args == Nil.INSTANCE)
                {
                    return Nil.INSTANCE;
                }
                Cons cons = (Cons) args;
                return lift(makeSymbol("cons"), cons.car, apply(cons.cdr));
            }
        },
        Map("map")
        {
            @Override
            public ISExp apply(IList args)
            {
                if(args == Nil.INSTANCE)
                {
                    return MNil.INSTANCE;
                }
                Cons cons = (Cons) args;
                Cons c1 = (Cons) cons.car;
                Cons c2 = (Cons) c1.cdr;
                return lift(makeSymbol("conm"), c1.car, c2.car, apply(cons.cdr));
            }
        };

        private static final ISExp quoteSymbol = makeSymbol("quote");

        private static ISExp quote(ISExp exp)
        {
            return new Cons(quoteSymbol, new Cons(exp, Nil.INSTANCE));
        }

        private final Symbol name;

        private PrimMacro(String name)
        {
            this.name = new Symbol(name);
        }

        @Override
        public ISExp getType(Unifier unifier)
        {
            return PrimTypes.Invalid.type;
        }

        @Override
        public String toString()
        {
            return "&" + name.value;
        }
    }
    enum PrimOp implements ICallableExp
    {
        Length("length", lift(PrimTypes.List.type), PrimTypes.Float.type)
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
        Mlength("mlength")
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

            @Override
            public ISExp getType(Unifier unifier)
            {
                ISExp r = unifier.getFreshVar();
                return absType(lift(prodType(r)), PrimTypes.Float.type);
            }
        },
        // FIXME: sum + prod
        Cons("cons")
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

            @Override
            public ISExp getType(Unifier unifier)
            {
                return absType(lift(unifier.getFreshVar(), PrimTypes.List.type), PrimTypes.List.type);
            }
        },
        // unsound, not total
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

            @Override
            public ISExp getType(Unifier unifier)
            {
                return absType(lift(PrimTypes.List.type), unifier.getFreshVar());
            }
        },
        // not total
        Cdr("cdr", lift(PrimTypes.List.type), PrimTypes.List.type)
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
        MCons("mcons")
        {
            @Override
            public ISExp apply(IList args)
            {
                Cons c1 = (Cons) args;
                Cons c2 = (Cons) c1.cdr;
                Cons c3 = (Cons) c2.cdr;
                ISExp label = labelValue(c1.car);
                ISExp value = c2.car;
                Map map = (Map) c3.car;
                ImmutableMap.Builder<ISExp, ISExp> builder = ImmutableMap.builder();
                builder.putAll(map.value);
                builder.put(label, value);
                return new Map(builder.build());
            }

            @Override
            public ISExp getType(Unifier unifier)
            {
                ISExp l = unifier.getFreshVar();
                ISExp r = unifier.getFreshVar();
                ISExp A = unifier.getFreshVar();
                unifier.addLacks(r, l);
                ISExp m = makeRow(l, A, r);
                return absType(lift(l, A, prodType(r)), prodType(m));
            }
        },
        MCar("mcar")
        {
            @Override
            public ISExp apply(IList args)
            {
                Cons c1 = (Cons) args;
                Cons c2 = (Cons) c1.cdr;
                Map map = (Map) c1.car;
                ISExp label = labelValue(c2.car);
                if(!map.value.containsKey(label))
                {
                    throw new IllegalStateException("mcar: " + map + " " + label);
                }
                return map.value.get(label);
            }

            @Override
            public ISExp getType(Unifier unifier)
            {
                ISExp l = unifier.getFreshVar();
                ISExp r = unifier.getFreshVar();
                ISExp A = unifier.getFreshVar();
                unifier.addLacks(r, l);
                ISExp m = makeRow(l, A, r);
                return absType(lift(prodType(m), l), A);
            }
        },
        MCdr("mcdr")
        {
            @Override
            public ISExp apply(IList args)
            {
                Cons c1 = (Cons) args;
                Cons c2 = (Cons) c1.cdr;
                Map map = (Map) c1.car;
                ISExp label = labelValue(c2.car);
                if(!map.value.containsKey(label))
                {
                    throw new IllegalStateException("mcar: " + map + " " + label);
                }
                ImmutableMap.Builder<ISExp, ISExp> builder = ImmutableMap.builder();
                for(java.util.Map.Entry<? extends ISExp, ? extends ISExp> entry : map.value.entrySet())
                {
                    if(!entry.getKey().equals(label))
                    {
                        builder.put(entry);
                    }
                }
                return new Map(builder.build());
            }

            @Override
            public ISExp getType(Unifier unifier)
            {
                ISExp l = unifier.getFreshVar();
                ISExp r = unifier.getFreshVar();
                ISExp A = unifier.getFreshVar();
                unifier.addLacks(r, l);
                ISExp m = makeRow(l, A, r);
                return absType(lift(prodType(m), l), prodType(r));
            }
        },
        Eq("eq")
        {
            @Override
            public ISExp apply(IList args)
            {
                Cons c1 = (Cons) args;
                Cons c2 = (Cons) c1.cdr;
                ISExp label = c1.car;
                ISExp value = c2.car;
                return lift(makeSymbol("&sum"), new Map(ImmutableMap.of(label, value)));
            }

            @Override
            public ISExp getType(Unifier unifier)
            {
                ISExp l = unifier.getFreshVar();
                ISExp r = unifier.getFreshVar();
                ISExp A = unifier.getFreshVar();
                unifier.addLacks(r, l);
                ISExp m = makeRow(l, A, r);
                return absType(lift(l, A), sumType(m));
            }
        },
        Or("or")
        {
            @Override
            public ISExp apply(IList args)
            {
                Cons c1 = (Cons) args;
                Cons c2 = (Cons) c1.cdr;
                ISExp label = c1.car;
                Cons sum = (Cons) c2.car;
                ISExp row = ((Cons) sum.cdr).car;
                return lift(makeSymbol("&sum"), row);
            }

            @Override
            public ISExp getType(Unifier unifier)
            {
                ISExp l = unifier.getFreshVar();
                ISExp r = unifier.getFreshVar();
                ISExp A = unifier.getFreshVar();
                unifier.addLacks(r, l);
                ISExp m = makeRow(l, A, r);
                return absType(lift(l, sumType(r)), sumType(m));
            }
        };

        static final ImmutableMap<Symbol, ISExp> values;

        static
        {
            ImmutableMap.Builder<Symbol, ISExp> builder = ImmutableMap.builder();
            for (PrimOp op : values())
            {
                builder.put(op.name, op);
            }
            for (PrimMacro op : PrimMacro.values())
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
            this.type = null;
        }

        private PrimOp(String name, IList args, ISExp ret)
        {
            this.name = new Symbol(name);
            this.type = absType(args, ret);
        }

        @Override
        public ISExp getType(Unifier unifier)
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
        public ISExp getType(Unifier unifier)
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
        public ISExp getType(Unifier unifier)
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
        public ISExp getType(Unifier unifier)
        {
            return emptyRow();
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
        public ISExp getType(Unifier unifier)
        {
            // TODO cache?
            ISExp cns = emptyRow();
            for(java.util.Map.Entry<? extends ISExp, ? extends ISExp> entry : value.entrySet())
            {
                cns = makeRow(labelType(entry.getKey()), entry.getValue().getType(unifier), cns);
            }
            ISExp var = unifier.getFreshVar();
            unifier.unify(var, cns);
            return prodType(var);
        }
    }

    static enum PrimTypes
    {
        Float("float"),
        String("string"),
        Symbol("symbol"),
        List("list"), // TODO: sum + product
        Invalid("invalid");

        final StringAtom type;

        PrimTypes(String type)
        {
            this.type = new StringAtom(type);
        }
    }

    static ISExp labelType(ISExp label)
    {
        return lift(makeString("label"), label);
    }

    static ISExp labelValue(ISExp label)
    {
        Cons c1 = (Cons) label;
        Cons c2 = (Cons) c1.cdr;
        return c2.car;
    }

    static ISExp absType(IList args, ISExp ret)
    {
        return new Cons(makeString("->"), new Cons(ret, args));
    }

    static ISExp prodType(ISExp row)
    {
        return lift(makeString("*"), row);
    }

    static ISExp sumType(ISExp row)
    {
        return lift(makeString("+"), row);
    }

    static ISExp emptyRow()
    {
        return lift(makeString("{}"));
    }

    static ISExp makeRow(ISExp l, ISExp A, ISExp r)
    {
        return lift(makeString("{}"), l, A, r);
    }

    /*static ISExp to(ISExp t, ISExp r)
    {
        Cons cons = (Cons) r;
        if(length(r) == 1)
        {
            return emptyRow();
        }
        Cons c2 = (Cons) cons.cdr;
        Cons c3 = (Cons) c2.cdr;
        Cons c4 = (Cons) c3.cdr;
        return makeRow(c2.car, absType(lift(c3.car), t), to(t, c3.car));
    }*/

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
