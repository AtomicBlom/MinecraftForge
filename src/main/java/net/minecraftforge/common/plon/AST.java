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

    private static final java.util.Map<String, Symbol> symbolCache = Maps.newIdentityHashMap();
    public static ISExp makeSymbol(String name)
    {
        if(!symbolCache.containsKey(name))
        {
            symbolCache.put(name, new Symbol(name));
        }
        return symbolCache.get(name);
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
        ISExp apply(Interpreter.Evaluator eval, IList args);
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
            return value.hashCode();
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
            this.value = value;
        }

        // symbols are interned, so only identity equality is possible
        @Override
        public boolean equals(Object o)
        {
            return this == o;
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
            return ops.hashCode();
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
        public FloatAtom apply(Interpreter.Evaluator eval, IList args)
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
            return absType(lift(PrimTypes.String.type), unifier.getFreshVar());
        }

        @Override
        public ISExp apply(Interpreter.Evaluator eval, IList args)
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
                return lift(makeSymbol("mcons"), c1.car, c2.car, apply(cons.cdr));
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
            this.name = (Symbol) makeSymbol(name);
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
        // correct mlength and mfold need Any
        /*Mlength("mlength")
        {
            @Override
            public FloatAtom apply(Interpreter.Evaluator eval, IList args)
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
                ISExp r = unifier.getFreshRow();
                return absType(lift(prodType(r)), PrimTypes.Float.type);
            }
        },*/
        Cons("cons")
        {
            @Override
            public ISExp apply(Interpreter.Evaluator eval, IList args)
            {
                Cons c1 = (Cons) args;
                Cons c2 = (Cons) c1.cdr;
                return new Cons(c1.car, (IList) c2.car);
            }

            @Override
            public ISExp getType(Unifier unifier)
            {
                ISExp car = unifier.getFreshVar();
                ISExp cdr = unifier.getFreshVar();
                ISExp prod = prodType(makeRow(labelType(makeString("car")), car, makeRow(labelType(makeString("cdr")), cdr, emptyRow())));
                ISExp ret = sumType(makeRow(labelType(makeString("cons")), prod, makeRow(labelType(makeString("nil")), unifier.getFreshVar(), emptyRow())));
                return absType(lift(car, cdr), ret);
            }
        },
        MCons("mcons")
        {
            @Override
            public ISExp apply(Interpreter.Evaluator eval, IList args)
            {
                Cons c1 = (Cons) args;
                Cons c2 = (Cons) c1.cdr;
                Cons c3 = (Cons) c2.cdr;
                ISExp label = c1.car;
                ISExp value = c2.car;
                ImmutableMap.Builder<ISExp, ISExp> builder = ImmutableMap.builder();
                if(c3.car != MNil.INSTANCE)
                {
                    builder.putAll(((Map) c3.car).value);
                }
                builder.put(label, value);
                return new Map(builder.build());
            }

            @Override
            public ISExp getType(Unifier unifier)
            {
                // Hmm
                ISExp l = unifier.getFreshLab();
                ISExp r = unifier.getFreshRow();
                ISExp A = unifier.getFreshVar();
                unifier.addLacks(r, l);
                ISExp m = makeRow(l, A, r);
                return absType(lift(l, A, prodType(r)), prodType(m));
            }
        },
        MCar("mcar")
        {
            @Override
            public ISExp apply(Interpreter.Evaluator eval, IList args)
            {
                Cons c1 = (Cons) args;
                Cons c2 = (Cons) c1.cdr;
                Map map = (Map) c1.car;
                ISExp label = c2.car;
                if(!map.value.containsKey(label))
                {
                    throw new IllegalStateException("mcar: " + map + " " + label);
                }
                return map.value.get(label);
            }

            @Override
            public ISExp getType(Unifier unifier)
            {
                ISExp l = unifier.getFreshLab();
                ISExp r = unifier.getFreshRow();
                ISExp A = unifier.getFreshVar();
                unifier.addLacks(r, l);
                ISExp m = unifier.getFreshRow();
                unifier.unify(prodType(m), prodType(makeRow(l, A, r)));
                return absType(lift(prodType(m), l), A);
            }
        },
        MCdr("mcdr")
        {
            @Override
            public ISExp apply(Interpreter.Evaluator eval, IList args)
            {
                Cons c1 = (Cons) args;
                Cons c2 = (Cons) c1.cdr;
                Map map = (Map) c1.car;
                ISExp label = c2.car;
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
                ISExp l = unifier.getFreshLab();
                ISExp r = unifier.getFreshRow();
                ISExp A = unifier.getFreshVar();
                unifier.addLacks(r, l);
                ISExp m = makeRow(l, A, r);
                return absType(lift(prodType(m), l), prodType(r));
            }
        },
        // Any needs compilation + evidence passing to work properly
        /*Any("any")
        {
            @Override
            public ISExp apply(Interpreter.Evaluator eval, IList args)
            {
                throw new UnsupportedOperationException("apply of #any");
            }

            @Override
            public ISExp getType(Unifier unifier)
            {
                ISExp l = unifier.getFreshLab();
                return l;
            }
        },*/
        Eq("eq")
        {
            @Override
            public ISExp apply(Interpreter.Evaluator eval, IList args)
            {
                Cons c1 = (Cons) args;
                Cons c2 = (Cons) c1.cdr;
                ISExp label = c1.car;
                ISExp value = c2.car;
                return makeSum(label, value);
            }

            @Override
            public ISExp getType(Unifier unifier)
            {
                ISExp l = unifier.getFreshLab();
                ISExp r = unifier.getFreshRow();
                ISExp A = unifier.getFreshVar();
                unifier.addLacks(r, l);
                ISExp m = makeRow(l, A, r);
                return absType(lift(l, A), sumType(m));
            }
        },
        Or("or")
        {
            @Override
            public ISExp apply(Interpreter.Evaluator eval, IList args)
            {
                Cons c1 = (Cons) args;
                Cons c2 = (Cons) c1.cdr;
                ISExp label = c1.car;
                ISExp sum = c2.car;
                // no need to store the label
                return sum;
            }

            @Override
            public ISExp getType(Unifier unifier)
            {
                ISExp l = unifier.getFreshLab();
                ISExp r = unifier.getFreshRow();
                ISExp A = unifier.getFreshVar();
                unifier.addLacks(r, l);
                ISExp m = makeRow(l, A, r);
                return absType(lift(l, sumType(r)), sumType(m));
            }
        },
        Has("has")
        {
            @Override
            public ISExp apply(Interpreter.Evaluator eval, IList args)
            {
                Cons c1 = (Cons) args;
                Cons c2 = (Cons) c1.cdr;
                Cons c3 = (Cons) c2.cdr;
                Cons c4 = (Cons) c3.cdr;

                if(getSumLabel(c2.car).equals(c1.car))
                {
                    return eval.apply(c3.car, lift(getSumValue(c2.car)));
                }
                return eval.apply(c4.car, lift(c2.car));
            }
            @Override
            public ISExp getType(Unifier unifier)
            {
                ISExp l = unifier.getFreshLab();
                ISExp r = unifier.getFreshRow();
                ISExp A = unifier.getFreshVar();
                ISExp B = unifier.getFreshVar();
                unifier.addLacks(r, l);
                ISExp m = makeRow(l, A, r);
                return absType(lift(
                    l,
                    sumType(m),
                    absType(lift(A), B),
                    absType(lift(sumType(r)), B)
                ), B);
            }
        },
        Case("case")
        {
            @Override
            public ISExp apply(Interpreter.Evaluator eval, IList args)
            {
                Cons c1 = (Cons) args;
                Cons c2 = (Cons) c1.cdr;
                Map prod = (Map) c2.car;
                ISExp label = getSumLabel(c1.car);
                ISExp value = getSumValue(c1.car);
                ISExp func = prod.value.get(label);
                return eval.apply(func, lift(value));
            }

            @Override
            public ISExp getType(Unifier unifier)
            {
                ISExp r = unifier.getFreshRow();
                ISExp A = unifier.getFreshVar();
                return absType(lift(sumType(r), prodType(to(A, r))), A);
            }
        },
        Select("select")
        {
            @Override
            public ISExp apply(Interpreter.Evaluator eval, IList args)
            {
                Cons c1 = (Cons) args;
                Cons c2 = (Cons) c1.cdr;
                Map prod = (Map) c1.car;
                ISExp label = getSumLabel(c1.car);
                ISExp value = prod.value.get(label);
                ISExp func = getSumValue(c2.car);
                return eval.apply(func, lift(value));
            }

            @Override
            public ISExp getType(Unifier unifier)
            {
                ISExp r = unifier.getFreshRow();
                ISExp A = unifier.getFreshVar();
                return absType(lift(prodType(r), sumType(to(A, r))), A);
            }
        },
        Product("product")
        {
            @Override
            public ISExp apply(Interpreter.Evaluator eval, IList args)
            {
                Cons c1 = (Cons) args;
                Cons c2 = (Cons) c1.cdr;
                Map prod = (Map) c2.car;
                if(prod.value.isEmpty())
                {
                    return MNil.INSTANCE;
                }
                ImmutableMap.Builder<ISExp, ISExp> builder = ImmutableMap.builder();
                for (java.util.Map.Entry<? extends ISExp, ? extends ISExp> entry : prod.value.entrySet())
                {
                    builder.put(entry.getKey(), eval.apply(entry.getValue(), lift(c1.car)));
                }
                return new Map(builder.build());
            }

            @Override
            public ISExp getType(Unifier unifier)
            {
                ISExp r = unifier.getFreshRow();
                ISExp A = unifier.getFreshVar();
                return absType(lift(A, prodType(from(A, r))), prodType(r));
            }
        },
        Sum("sum")
        {
            @Override
            public ISExp apply(Interpreter.Evaluator eval, IList args)
            {
                Cons c1 = (Cons) args;
                Cons c2 = (Cons) c1.cdr;
                return makeSum(getSumLabel(c2.car), eval.apply(getSumValue(c2.car), lift(c1.car)));
            }

            @Override
            public ISExp getType(Unifier unifier)
            {
                ISExp r = unifier.getFreshRow();
                ISExp A = unifier.getFreshVar();
                return absType(lift(A, sumType(from(A, r))), sumType(r));
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
            this.name = (Symbol) makeSymbol(name);
            this.type = null;
        }

        private PrimOp(String name, IList args, ISExp ret)
        {
            this.name = (Symbol) makeSymbol(name);
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
            return sumType(makeRow(labelType(makeString("cons")), unifier.getFreshVar(), makeRow(labelType(makeString("nil")), prodType(emptyRow()), emptyRow())));
        }
    }

    static final class Cons implements IList
    {
        final ISExp car;
        final IList cdr;
        final String context;
        final int hashCode;

        Cons(ISExp car, IList cdr)
        {
            this(car, cdr, null);
        }

        Cons(ISExp car, IList cdr, String context)
        {
            this.car = car;
            this.cdr = cdr;
            this.context = context;
            this.hashCode = Objects.hashCode(car, cdr);
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
            return hashCode;
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
            ISExp carType = car.getType(unifier);
            ISExp cdrType = car.getType(unifier);
            ISExp prod = prodType(makeRow(labelType(makeString("car")), carType, makeRow(labelType(makeString("cdr")), cdrType, emptyRow())));
            return sumType(makeRow(labelType(makeString("cons")), prod, makeRow(labelType(makeString("nil")), unifier.getFreshVar(), emptyRow())));
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
            return prodType(emptyRow());
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
                if (!isStringLabelExp(exp))
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
            ISExp row = emptyRow();
            for(java.util.Map.Entry<? extends ISExp, ? extends ISExp> entry : value.entrySet())
            {
                // label type is the same as the label here, since not evaluating
                row = makeRow(entry.getKey(), entry.getValue().getType(unifier), row);
            }
            return prodType(row);
        }
    }

    static enum PrimTypes
    {
        Float("float"),
        String("string"),
        Symbol("symbol"),
        Invalid("invalid");

        final ISExp type;

        PrimTypes(String type)
        {
            this.type = makeString(type);
        }
    }

    static ISExp makeSum(ISExp label, ISExp value)
    {
        return new Map(ImmutableMap.of(label, value));
    }

    static ISExp getSumLabel(ISExp sum)
    {
        if(sum == Nil.INSTANCE)
        {
            return labelType(makeString("nil"));
        }
        else if(sum instanceof Cons)
        {
            return labelType(makeString("cons"));
        }
        else
        {
            return ((Map) sum).value.keySet().iterator().next();
        }
    }

    static ISExp getSumValue(ISExp sum)
    {
        if(sum == Nil.INSTANCE)
        {
            return new Map(ImmutableMap.<ISExp, ISExp>of());
        }
        else if (sum instanceof Cons)
        {
            Cons cons = (Cons) sum;
            return new Map(ImmutableMap.of(
                labelType(makeString("car")), cons.car,
                labelType(makeString("cdr")), cons.cdr
            ));
        }
        else
        {
            return ((Map) sum).value.values().iterator().next();
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

    static boolean isLabelExp(ISExp exp)
    {
        return exp instanceof Symbol || (exp instanceof Cons && length(exp) == 2 && ((Cons) exp).car.equals(makeString("label")));
    }

    static boolean isStringLabelExp(ISExp exp)
    {
        return exp instanceof Symbol || (exp instanceof Cons && length(exp) == 2 && ((Cons) exp).car.equals(makeString("label")) && ((Cons) ((Cons) exp).cdr).car instanceof StringAtom);
    }

    static String sugarLabel(ISExp exp)
    {
        Cons c1 = (Cons) exp;
        Cons c2 = (Cons) c1.cdr;
        return ":" + ((StringAtom) c2.car).value;
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

    static boolean isFunc(ISExp exp)
    {
        return exp instanceof Cons && ((Cons) exp).car.equals(makeString("->"));
    }

    static boolean isProd(ISExp exp)
    {
        return exp instanceof Cons && ((Cons) exp).car.equals(makeString("*"));
    }

    static boolean isSum(ISExp exp)
    {
        return exp instanceof Cons && ((Cons) exp).car.equals(makeString("+"));
    }

    static boolean isRow(ISExp exp)
    {
        if(exp instanceof Cons && ((Cons) exp).car instanceof StringAtom)
        {
            String name = ((StringAtom) ((Cons) exp).car).value;
            return name.equals("{}") || name.equals("from") || name.equals("to");
        }
        return false;
    }

    static ISExp emptyRow()
    {
        return lift(makeString("{}"));
    }

    static ISExp makeRow(ISExp l, ISExp t, ISExp r)
    {
        if(!isLabelExp(l))
        {
            throw new IllegalStateException("Expecting label type, got: " + l);
        }
        if(r instanceof Cons)
        {
            Cons c1 = (Cons) r;
            if(length(c1) != 1)
            {
                Cons c2 = (Cons) c1.cdr;
                Cons c3 = (Cons) c2.cdr;
                Cons c4 = (Cons) c3.cdr;

                ISExp l2 = c2.car;
                ISExp t2 = c3.car;
                ISExp r2 = c4.car;

                if (l.toString().compareTo(l2.toString()) >= 0)
                {
                    return lift(makeString("{}"), l2, t2, makeRow(l, t, r2));
                }
            }
        }
        return lift(makeString("{}"), l, t, r);
    }

    // FIXME: make more efficient
    static ISExp sortRow(ISExp r)
    {
        if(r instanceof Cons)
        {
            Cons c1 = (Cons) r;
            if(length(c1) == 4)
            {
                Cons c2 = (Cons) c1.cdr;
                Cons c3 = (Cons) c2.cdr;
                Cons c4 = (Cons) c3.cdr;

                ISExp l = c2.car;
                ISExp t = c3.car;
                ISExp r2 = c4.car;

                return makeRow(l, t, r2);
            }
        }
        return r;
    }

    static ISExp from(ISExp t, ISExp r)
    {
        return lift(makeString("from"), t, r);
    }

    static ISExp to(ISExp t, ISExp r)
    {
        return lift(makeString("to"), t, r);
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
                    else if(parameter instanceof StringAtom)
                    {
                        out.value(" " + ((StringAtom) parameter).value);
                    }
                    else if (parameter == Nil.INSTANCE)
                    {
                        out.beginArray();
                        out.endArray();
                    }
                    else if (parameter instanceof Cons)
                    {
                        if(isStringLabelExp(parameter))
                        {
                            out.value(sugarLabel(parameter));
                        }
                        else
                        {
                            out.beginArray();
                            write(out, ((Cons) parameter).car);
                            for (IList cdr = ((Cons) parameter).cdr; cdr instanceof Cons; cdr = ((Cons) cdr).cdr)
                            {
                                write(out, ((Cons) cdr).car);
                            }
                            out.endArray();
                        }
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
                                String name;
                                if(entry.getKey() instanceof IStringAtom)
                                {
                                    name = ((IStringAtom) entry.getKey()).value();
                                }
                                else
                                {
                                    name = sugarLabel(entry.getKey());
                                }
                                out.name(name);
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
                    else
                    {
                        out.nullValue();
                    }
                }

                private IList readCdr(JsonReader in) throws IOException
                {
                    if (!in.hasNext())
                    {
                        return Nil.INSTANCE;
                    }
                    String context = in.toString();
                    return new Cons(read(in), readCdr(in), context);
                }

                private ISExp parseString(String string)
                {
                    if (string.startsWith("#"))
                    {
                        return makeSymbol(string.substring(1).intern());
                    }
                    if (string.startsWith(" "))
                    {
                        return makeString(string.substring(1));
                    }
                    if (string.startsWith(":"))
                    {
                        return lift(makeSymbol("label"), makeString(string.substring(1)));
                    }
                    if (opsPattern.matcher(string).matches())
                    {
                        return new ArithmSymbol(string);
                    }
                    throw new JsonParseException("Unknown string: \"" + string + "\"");
                }

                public ISExp read(JsonReader in) throws IOException
                {
                    String context = in.toString();
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
