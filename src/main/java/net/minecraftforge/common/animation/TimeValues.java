package net.minecraftforge.common.animation;

import java.io.IOException;
import java.util.regex.Pattern;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonParseException;
import com.google.gson.stream.JsonToken;
import net.minecraft.util.IStringSerializable;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.apache.commons.lang3.NotImplementedException;

/**
 * Various implementations of ITimeValue.
 */
public final class TimeValues
{
    public static enum IdentityValue implements ITimeValue, IStringSerializable
    {
        INSTANCE;

        public float apply(float input)
        {
            return input;
        }

        public String getName()
        {
            return "identity";
        }
    }

    public static final class ConstValue implements ITimeValue
    {
        private final float output;

        public ConstValue(float output)
        {
            this.output = output;
        }

        public float apply(float input)
        {
            return output;
        }

        @Override
        public int hashCode()
        {
            return Objects.hashCode(output);
        }

        @Override
        public boolean equals(Object obj)
        {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            ConstValue other = (ConstValue) obj;
            return output == other.output;
        }
    }

    /**
     * Simple value holder.
     */
    public static final class VariableValue implements ITimeValue
    {
        private float output;

        public VariableValue(float initialValue)
        {
            this.output = initialValue;
        }

        public void setValue(float newValue)
        {
            this.output = newValue;
        }

        public float apply(float input)
        {
            return output;
        }

        @Override
        public int hashCode()
        {
            return Objects.hashCode(output);
        }

        @Override
        public boolean equals(Object obj)
        {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            VariableValue other = (VariableValue) obj;
            return output == other.output;
        }
    }

    public static final class SimpleExprValue implements ITimeValue
    {
        private static final Pattern opsPattern = Pattern.compile("[+\\-*/mMrRfF]+");

        private final String operators;
        private final ImmutableList<ITimeValue> args;

        public SimpleExprValue(String operators, ImmutableList<ITimeValue> args)
        {
            this.operators = operators;
            this.args = args;
        }

        public float apply(float input)
        {
            float ret = input;
            for(int i = 0; i < operators.length(); i++)
            {
                float arg = args.get(i).apply(input);
                switch(operators.charAt(i))
                {
                    case '+': ret += arg; break;
                    case '-': ret -= arg; break;
                    case '*': ret *= arg; break;
                    case '/': ret /= arg; break;
                    case 'm': ret = Math.min(ret, arg); break;
                    case 'M': ret = Math.max(ret, arg); break;
                    case 'r': ret = (float)Math.floor(ret / arg) * arg; break;
                    case 'R': ret = (float)Math.ceil(ret / arg) * arg; break;
                    case 'f': ret -= Math.floor(ret / arg) * arg; break;
                    case 'F': ret = (float)Math.ceil(ret / arg) * arg - ret; break;
                }
            }
            return ret;
        }

        @Override
        public int hashCode()
        {
            return Objects.hashCode(operators, args);
        }

        @Override
        public boolean equals(Object obj)
        {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            SimpleExprValue other = (SimpleExprValue) obj;
            return Objects.equal(operators, other.operators) && Objects.equal(args, other.args);
        }
    }

    public static final class CompositionValue implements ITimeValue
    {
        private final ITimeValue g;
        private final ITimeValue f;

        public CompositionValue(ITimeValue g, ITimeValue f)
        {
            super();
            this.g = g;
            this.f = f;
        }

        public float apply(float input)
        {
            return g.apply(f.apply(input));
        }

        @Override
        public int hashCode()
        {
            return Objects.hashCode(g, f);
        }

        @Override
        public boolean equals(Object obj)
        {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            CompositionValue other = (CompositionValue) obj;
            return Objects.equal(g, other.g) && Objects.equal(f, other.f);
        }
    }

    public static final class ParameterValue implements ITimeValue, IStringSerializable
    {
        private final String parameterName;
        private final Function<String, ITimeValue> valueResolver;
        private ITimeValue parameter;

        public ParameterValue(String parameterName, Function<String, ITimeValue> valueResolver)
        {
            this.parameterName = parameterName;
            this.valueResolver = valueResolver;
        }

        public String getName()
        {
            return parameterName;
        }

        private void resolve()
        {
            if(parameter == null)
            {
                if(valueResolver != null)
                {
                    parameter = valueResolver.apply(parameterName);
                }
                if(parameter == null)
                {
                    throw new IllegalArgumentException("Couldn't resolve parameter value " + parameterName);
                }
            }
        }

        public float apply(float input)
        {
            resolve();
            return parameter.apply(input);
        }

        @Override
        public int hashCode()
        {
            resolve();
            return parameter.hashCode();
        }

        @Override
        public boolean equals(Object obj)
        {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            ParameterValue other = (ParameterValue) obj;
            resolve();
            other.resolve();
            return Objects.equal(parameter, other.parameter);
        }
    }

    public static enum CommonTimeValueTypeAdapterFactory implements TypeAdapterFactory
    {
        INSTANCE;

        private final ThreadLocal<Function<String, ITimeValue>> valueResolver = new ThreadLocal<Function<String, ITimeValue>>();

        public void setValueResolver(Function<String, ITimeValue> valueResolver)
        {
            this.valueResolver.set(valueResolver);
        }

        @SuppressWarnings("unchecked")
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type)
        {
            if(type.getRawType() != ITimeValue.class)
            {
                return null;
            }

            return (TypeAdapter<T>)new TypeAdapter<ITimeValue>()
            {
                public void write(JsonWriter out, ITimeValue parameter) throws IOException
                {
                    if(parameter instanceof ConstValue)
                    {
                        out.value(((ConstValue)parameter).output);
                    }
                    else if(parameter instanceof SimpleExprValue)
                    {
                        SimpleExprValue p = (SimpleExprValue)parameter;
                        out.beginArray();
                        out.value(p.operators);
                        for(ITimeValue v : p.args)
                        {
                            write(out, v);
                        }
                        out.endArray();
                    }
                    else if(parameter instanceof CompositionValue)
                    {
                        CompositionValue p = (CompositionValue)parameter;
                        out.beginArray();
                        out.value("compose");
                        write(out, p.g);
                        write(out, p.f);
                        out.endArray();
                    }
                    else if(parameter instanceof IStringSerializable)
                    {
                        out.value("#" + ((IStringSerializable)parameter).getName());
                    }
                }

                public ITimeValue read(JsonReader in) throws IOException
                {
                    switch(in.peek())
                    {
                    case NUMBER:
                        return new ConstValue((float)in.nextDouble());
                    case BEGIN_ARRAY:
                        in.beginArray();
                        String type = in.nextString();
                        ITimeValue p;
                        if(SimpleExprValue.opsPattern.matcher(type).matches())
                        {
                            ImmutableList.Builder<ITimeValue> builder = ImmutableList.builder();
                            while(in.hasNext())
                            {
                                builder.add(read(in));
                            }
                            p = new SimpleExprValue(type, builder.build());
                        }
                        else if("compose".equals(type))
                        {
                            p = new CompositionValue(read(in), read(in));
                        }
                        else
                        {
                            throw new IOException("Unknown TimeValue type \"" + type + "\"");
                        }
                        in.endArray();
                        return p;
                    case STRING:
                        String string = in.nextString();
                        if(string.equals("#identity"))
                        {
                            return IdentityValue.INSTANCE;
                        }
                        if(!string.startsWith("#"))
                        {
                            throw new IOException("Expected TimeValue reference, got \"" + string + "\"");
                        }
                        // User Parameter TimeValue
                        return new ParameterValue(string.substring(1), valueResolver.get());
                    default:
                        throw new IOException("Expected TimeValue, got " + in.peek());
                    }
                }
            };
        }
    }

    public static interface ISExp {}
    public static interface IAtom extends ISExp {}
    public static interface IStringAtom extends IAtom
    {
        String value();
    }

    public static final class FloatAtom implements IAtom
    {
        private final float value;

        public FloatAtom(float value)
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
    }

    public static final class Identifier implements IStringAtom
    {
        private final String value;

        public Identifier(String value)
        {
            this.value = value;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Identifier that = (Identifier) o;
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
            return "\"#" + value + "\"";
        }

        @Override
        public String value()
        {
            return "#" + value;
        }
    }

    public static final class BuiltinIdentifier implements IStringAtom
    {
        private final String value;

        public BuiltinIdentifier(String value)
        {
            this.value = value;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Identifier that = (Identifier) o;
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
            return "\"&" + value + "\"";
        }

        @Override
        public String value()
        {
            return "&" + value;
        }
    }

    public enum PrimOp implements IAtom
    {
        Length("length")
        {
            @Override
            public FloatAtom apply(IList args)
            {
                if(args == Nil.INSTANCE)
                {
                    throw new IllegalArgumentException("Length with no arguments");
                }
                Cons cons = (Cons) args;
                if(cons.cdr != Nil.INSTANCE)
                {
                    throw new IllegalArgumentException("Length has too many arguments: " + cons);
                }
                if(cons.car == Nil.INSTANCE || cons.car == MNil.INSTANCE)
                {
                    return new FloatAtom(0);
                }
                if(cons.car instanceof Cons)
                {
                    return new FloatAtom(1 + apply(new Cons(((Cons) cons.car).cdr, Nil.INSTANCE)).value);
                }
                if(cons.car instanceof Map)
                {
                    return new FloatAtom(((Map) cons.car).value.size());
                }
                throw new IllegalArgumentException("Length called neither on a list nor on a map");
            }
        },
        Cons("cons")
        {
            @Override
            public ISExp apply(IList args)
            {
                if(args == Nil.INSTANCE)
                {
                    throw new IllegalArgumentException("Cons with no arguments");
                }
                Cons c1 = (Cons) args;
                if(c1.cdr == Nil.INSTANCE)
                {
                    throw new IllegalArgumentException("Cons with only 1 argument");
                }
                Cons c2 = (Cons) c1.cdr;
                if(!(c2.car instanceof IList))
                {
                    throw new IllegalArgumentException("Cons needs a list as a second argument");
                }
                if(c2.cdr != Nil.INSTANCE)
                {
                    throw new IllegalArgumentException("Cons with too many arguments: " + c2.cdr);
                }
                return new Cons(c1.car, (IList) c2.car);
            }
        },
        Car("car")
        {
            @Override
            public ISExp apply(IList args)
            {
                if(args == Nil.INSTANCE)
                {
                    throw new IllegalArgumentException("Car with no arguments");
                }
                Cons cons = (Cons) args;
                if(cons.cdr != Nil.INSTANCE)
                {
                    throw new IllegalArgumentException("Car has too many arguments: " + cons);
                }
                if(cons.car instanceof Cons)
                {
                    return ((Cons) cons.car).car;
                }
                throw new IllegalArgumentException("Car called not on a list");
            }
        },
        Cdr("cdr")
        {
            @Override
            public ISExp apply(IList args)
            {
                if(args == Nil.INSTANCE)
                {
                   throw new IllegalArgumentException("Cdr with no arguments");
                }
                Cons cons = (Cons) args;
                if(cons.cdr != Nil.INSTANCE)
                {
                    throw new IllegalArgumentException("Cdr has too many arguments: " + cons);
                }
                if(cons.car instanceof Cons)
                {
                    return ((Cons) cons.car).cdr;
                }
                throw new IllegalArgumentException("Cdr called not on a list");
            }
        },
        Map("map")
        {
            @Override
            public ISExp apply(IList args)
            {
                throw new NotImplementedException("map");
            }
        },
        Conm("conm")
        {
            @Override
            public ISExp apply(IList args)
            {

                throw new NotImplementedException("conm");
            }
        };

        private static final ImmutableMap<Identifier, PrimOp> values;
        static
        {
            ImmutableMap.Builder<Identifier, PrimOp> builder = ImmutableMap.builder();
            for(PrimOp op : values())
            {
                builder.put(op.name, op);
            }
            values = builder.build();
        }
        private final Identifier name;

        PrimOp(String name)
        {
            this.name = new Identifier(name);
        }

        public abstract ISExp apply(IList args);
    }

    public static interface IList extends ISExp {}

    public static enum Nil implements IList
    {
        INSTANCE;

        @Override
        public String toString()
        {
            return "[]";
        }
    }

    public static final class Cons implements IList
    {
        private final ISExp car;
        private final IList cdr;

        public Cons(ISExp car, IList cdr)
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
            return Objects.equal(car, cons.car) &&
                    Objects.equal(cdr, cons.cdr);
        }

        @Override
        public int hashCode()
        {
            return Objects.hashCode(car, cdr);
        }

        private String cdrString()
        {
            if(cdr == Nil.INSTANCE)
            {
                return "";
            }
            Cons cdr = (Cons)this.cdr;
            return ", " + cdr.car.toString() + cdr.cdrString();
        }

        @Override
        public String toString()
        {
            return "[" + car + cdrString() + "]";
        }
    }

    public static interface IMap extends ISExp {}

    public static enum MNil implements IMap
    {
        INSTANCE;
        @Override
        public String toString()
        {
            return "{}";
        }
    }

    public static final class Map implements IMap
    {
        private final ImmutableMap<? extends ISExp, ? extends ISExp> value;
        private final transient boolean isJsonifiable;

        public Map(ImmutableMap<? extends ISExp, ? extends ISExp> value)
        {
            this.value = value;
            boolean isJsonifiable = true;
            for(ISExp exp : value.keySet())
            {
                if(!(exp instanceof Identifier))
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
            if(isJsonifiable)
            {
                return "{ " + Joiner.on(", ").withKeyValueSeparator(": ").join(value) + " }";
            }
            return "[\"#&map\", " + Joiner.on(", ").withKeyValueSeparator(", ").join(value) + "]";
        }
    }

    private static IList bind(IList argNames, IList args, IList env)
    {
        if(!PrimOp.Length.apply(new Cons(argNames, Nil.INSTANCE)).equals(PrimOp.Length.apply(new Cons(args, Nil.INSTANCE))))
        {
            throw new IllegalArgumentException("called bind with lists of different length");
        }
        ImmutableMap.Builder<ISExp, ISExp> builder = ImmutableMap.builder();
        while(argNames != Nil.INSTANCE)
        {
            builder.put(((Cons)argNames).car, ((Cons)args).car);
            argNames = ((Cons) argNames).cdr;
            args = ((Cons) args).cdr;
        }
        ImmutableMap<ISExp, ISExp> frame = builder.build();
        IMap map;
        if(frame.isEmpty())
        {
            map = MNil.INSTANCE;
        }
        else
        {
            map = new Map(frame);
        }
        return new Cons(map, env);

    }

    private static ISExp lookup(IList env, Identifier name)
    {
        if(env == Nil.INSTANCE)
        {
            return new Identifier("&unbound");
        }
        Cons cons = (Cons) env;
        if(cons.car == MNil.INSTANCE)
        {
            return lookup(cons.cdr, name);
        }
        if(cons.car instanceof Map)
        {
            Map map = (Map) cons.car;
            if(map.value.containsKey(name))
            {
                return map.value.get(name);
            }
            return lookup(cons.cdr, name);
        }
        throw new IllegalArgumentException("lookup called with a list that has something other than a map:" + cons.car);
    }

    public static ISExp eval(ISExp exp)
    {
        return eval(exp, new Cons(new Map(PrimOp.values), Nil.INSTANCE));
    }
    private static ISExp eval(ISExp exp, IList env)
    {
        if(exp instanceof FloatAtom)
        {
            return exp;
        }
        else if(exp instanceof Identifier)
        {
            return lookup(env, (Identifier) exp);
        }
        else if(exp instanceof Cons)
        {
            Cons cons = (Cons) exp;
            if(new Identifier("quote").equals(cons.car))
            {
                if(cons.cdr == Nil.INSTANCE)
                {
                    throw new IllegalArgumentException("quote with no arguments");
                }
                Cons cdr = (Cons)cons.cdr;
                if(cdr.cdr != Nil.INSTANCE)
                {
                    throw new IllegalArgumentException("quote with too many arguments: " + exp);
                }
                return cdr.car;
            }
            else if(new Identifier("lambda").equals(cons.car))
            {
                if(cons.cdr == Nil.INSTANCE)
                {
                    throw new IllegalArgumentException("lambda with no arguments");
                }
                Cons c1 = (Cons)cons.cdr;
                ISExp args = c1.car;
                if(c1.cdr == Nil.INSTANCE)
                {
                    throw new IllegalArgumentException("lambda with 1 argument");
                }
                Cons c2 = (Cons) c1.cdr;
                ISExp body = c2.car;
                if(c2.cdr != Nil.INSTANCE)
                {
                    throw new IllegalArgumentException("lambda with too many arguments: " + exp);
                }
                return new Cons(new Identifier("&function"), new Cons(args, new Cons(body, new Cons(env, Nil.INSTANCE))));
            }
            else
            {
                return apply(eval(cons.car, env), evlis(cons.cdr, env));
            }
        }
        throw new IllegalStateException("eval of " + exp);
    }

    private static ISExp apply(ISExp func, IList args)
    {
        if(func instanceof PrimOp)
        {
            return ((PrimOp)func).apply(args);
        }
        if(func instanceof Cons)
        {
            Cons c1 = (Cons) func;
            if(new Identifier("&function").equals(c1.car) && c1.cdr != Nil.INSTANCE)
            {
                Cons c2 = (Cons) c1.cdr;
                if(c2.car instanceof IList && c2.cdr != Nil.INSTANCE)
                {
                    IList argNames = (IList) c2.car;
                    Cons c3 = (Cons) c2.cdr;
                    ISExp body = c3.car;
                    if(c3.cdr != Nil.INSTANCE)
                    {
                        Cons c4 = (Cons) c3.cdr;
                        ISExp env = c4.car;
                        if(env instanceof IList && c4.cdr == Nil.INSTANCE)
                        {
                            return eval(body, bind(argNames, args, (IList) env));
                        }
                    }
                }
            }
        }
        throw new IllegalArgumentException("Don't know how to apply: " + func);
    }

    private static IList evlis(IList list, IList env)
    {
        if(list == Nil.INSTANCE)
        {
            return Nil.INSTANCE;
        }
        Cons cons = (Cons) list;
        return new Cons(eval(cons.car, env), evlis(cons.cdr, env));
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
                    if(parameter instanceof FloatAtom)
                    {
                        out.value(((FloatAtom) parameter).value);
                    }
                    else if(parameter instanceof Identifier)
                    {
                        out.value(((Identifier) parameter).value);
                    }
                    else if(parameter == Nil.INSTANCE)
                    {
                        out.beginArray();
                        out.endArray();
                    }
                    else if(parameter instanceof Cons)
                    {
                        out.beginArray();
                        write(out, ((Cons) parameter).car);
                        for(IList cdr = ((Cons) parameter).cdr; cdr instanceof Cons; cdr = ((Cons) cdr).cdr)
                        {
                            write(out, ((Cons) cdr).car);
                        }
                        out.endArray();
                    }
                    else if(parameter == MNil.INSTANCE)
                    {
                        out.beginObject();
                        out.endObject();
                    }
                    else if(parameter instanceof Map)
                    {

                    }
                }

                private IList readCdr(JsonReader in) throws IOException
                {
                    if(in.peek() == JsonToken.END_ARRAY)
                    {
                        return Nil.INSTANCE;
                    }
                    return new Cons(read(in), readCdr(in));
                }

                public ISExp read(JsonReader in) throws IOException
                {
                    switch(in.peek())
                    {
                        case STRING:
                            String atom = in.nextString();
                            if(atom.startsWith("#"))
                            {
                                return new Identifier(atom.substring(1));
                            }
                            throw new JsonParseException("Unknown string: \"" + atom + "\"");
                        case NUMBER:
                            return new FloatAtom((float)in.nextDouble());
                        case BEGIN_ARRAY:
                            in.beginArray();
                            IList list = readCdr(in);
                            in.endArray();
                            return list;
                        default:
                            throw new JsonParseException("Unexpected item in the bagging area: \"" + in.peek() + "\"");
                    }
                }
            };
        }
    }
}
